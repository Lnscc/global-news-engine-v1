package com.example.globalnewsenginev1.stories.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class StoryEmbeddingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryEmbeddingService.class);
    private static final List<Duration> RETRY_BACKOFF = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1), Duration.ofHours(6));

    private final StoryEmbeddingRepository repository;
    private final StoryTitleNormalizer normalizer;
    private final EmbeddingClient client;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final StoryEmbeddingMetrics metrics;

    @Autowired
    StoryEmbeddingService(
            StoryEmbeddingRepository repository,
            StoryTitleNormalizer normalizer,
            EmbeddingClient client,
            PlatformTransactionManager transactionManager,
            StoryEmbeddingMetrics metrics
    ) {
        this(repository, normalizer, client, new TransactionTemplate(transactionManager),
                Clock.systemUTC(), metrics);
    }

    StoryEmbeddingService(
            StoryEmbeddingRepository repository,
            StoryTitleNormalizer normalizer,
            EmbeddingClient client,
            TransactionTemplate transactionTemplate,
            Clock clock,
            StoryEmbeddingMetrics metrics
    ) {
        this.repository = repository;
        this.normalizer = normalizer;
        this.client = client;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.metrics = metrics;
    }

    public ProcessingResult processIncremental(int batchSize) {
        return process(batchSize, false);
    }

    public ProcessingResult repair(int batchSize) {
        return process(batchSize, true);
    }

    private ProcessingResult process(int batchSize, boolean repairRun) {
        List<StoryEmbeddingRepository.ArticleCandidate> candidates =
                repository.findCandidates(batchSize, repairRun);
        int processed = 0;
        int failed = 0;
        for (StoryEmbeddingRepository.ArticleCandidate candidate : candidates) {
            try {
                Boolean completed = transactionTemplate.execute(status -> processCandidate(candidate));
                if (Boolean.TRUE.equals(completed)) {
                    processed++;
                    metrics.processed();
                }
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.error("Story title input failed for articleRef={} version={}",
                        candidate.articleRef(), candidate.versionKey(), exception);
            }
        }
        return new ProcessingResult(candidates.size(), processed, failed);
    }

    private boolean processCandidate(StoryEmbeddingRepository.ArticleCandidate candidate) {
        repository.lockArticle(candidate.articleId());
        TitleInput title = normalizer.normalize(candidate.title());
        Instant now = clock.instant();
        Instant effectiveAt = candidate.publishedAt() != null
                ? candidate.publishedAt() : candidate.firstSeenAt();
        String effectiveAtSource = candidate.publishedAt() != null ? "PUBLISHED_AT" : "FIRST_SEEN_AT";
        StoryEmbeddingRepository.Artifact artifact = null;

        if (title.usability() == TitleInput.TitleUsability.USABLE) {
            long artifactId = repository.ensureArtifact(candidate, title.titleInputHash(), now);
            artifact = repository.lockArtifact(artifactId).orElse(null);
            if (artifact == null) {
                return false;
            }
            artifact = processArtifact(candidate, title, artifact);
        }

        String fingerprint = fingerprint(candidate, title, artifact, effectiveAt, effectiveAtSource);
        repository.synchronizeInput(candidate, title, artifact, fingerprint,
                effectiveAt, effectiveAtSource, clock.instant());
        return true;
    }

    private StoryEmbeddingRepository.Artifact processArtifact(
            StoryEmbeddingRepository.ArticleCandidate candidate,
            TitleInput title,
            StoryEmbeddingRepository.Artifact artifact
    ) {
        if ("READY".equals(artifact.status()) || "TERMINAL_FAILURE".equals(artifact.status())
                || artifact.attemptCount() >= 5 || !client.isAvailable()
                || (artifact.nextRetryAt() != null && artifact.nextRetryAt().isAfter(clock.instant()))) {
            return artifact;
        }
        int attempt = artifact.attemptCount() + 1;
        Instant startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        metrics.modelCall();
        metrics.attempt();
        try {
            EmbeddingClient.EmbeddingResponse response = client.embed(
                    candidate.embeddingModelId(), candidate.embeddingDimension(),
                    List.of(title.normalizedTitle())).getFirst();
            EmbeddingVectorCodec.EncodedVector encoded = EmbeddingVectorCodec.encode(
                    response.vector(), candidate.embeddingDimension());
            Instant completedAt = clock.instant();
            if ("READY".equals(artifact.status()) && !encoded.hash().equals(artifact.vectorHash())) {
                repository.recordModelDrift(artifact.id(), attempt, response.providerRequestId(),
                        response.vector().length, encoded.hash(), startedAt, completedAt);
                return artifact;
            }
            repository.completeReady(artifact.id(), attempt, encoded, response.providerRequestId(),
                    response.vector().length, startedAt, completedAt);
            return new StoryEmbeddingRepository.Artifact(
                    artifact.id(), "READY", encoded.hash(), attempt, null);
        } catch (EmbeddingVectorCodec.InvalidVectorException exception) {
            Instant completedAt = clock.instant();
            repository.completeFailure(artifact.id(), attempt, "TERMINAL_FAILURE", null,
                    exception.code(), exception.getMessage(), null, startedAt, completedAt, null);
            metrics.failure();
            return new StoryEmbeddingRepository.Artifact(
                    artifact.id(), "TERMINAL_FAILURE", null, attempt, null);
        } catch (EmbeddingClientException exception) {
            Instant completedAt = clock.instant();
            boolean retryable = exception.retryable();
            Instant nextRetry = retryable
                    ? nextRetryAt(attempt, completedAt, exception.retryAfter()) : null;
            String status = retryable ? "RETRYABLE_FAILURE" : "TERMINAL_FAILURE";
            repository.completeFailure(artifact.id(), attempt, status, exception.providerRequestId(),
                    exception.errorCode(), exception.getMessage(), null,
                    startedAt, completedAt, nextRetry);
            metrics.failure();
            return new StoryEmbeddingRepository.Artifact(
                    artifact.id(), status, null, attempt, nextRetry);
        } finally {
            metrics.recordLatency(System.nanoTime() - startedNanos);
        }
    }

    private String fingerprint(
            StoryEmbeddingRepository.ArticleCandidate candidate,
            TitleInput title,
            StoryEmbeddingRepository.Artifact artifact,
            Instant effectiveAt,
            String effectiveAtSource
    ) {
        String canonical = String.join("\n",
                candidate.articleRef(),
                effectiveAt.toString(),
                effectiveAtSource,
                title.usability().name(),
                value(title.titleInputHash()),
                candidate.titleNormalizationVersion(),
                candidate.genericTitleRuleVersion(),
                candidate.embeddingModelId(),
                candidate.embeddingModelVersion(),
                Integer.toString(candidate.embeddingDimension()),
                artifact == null ? "" : Long.toString(artifact.id()),
                artifact == null ? "" : value(artifact.vectorHash()));
        return StoryTitleNormalizer.sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    static Instant nextRetryAt(int attempt, Instant completedAt, Duration retryAfter) {
        if (attempt >= 5) {
            return null;
        }
        return completedAt.plus(retryAfter != null ? retryAfter : RETRY_BACKOFF.get(attempt - 1));
    }

    public record ProcessingResult(int selected, int processed, int failed) {
    }
}
