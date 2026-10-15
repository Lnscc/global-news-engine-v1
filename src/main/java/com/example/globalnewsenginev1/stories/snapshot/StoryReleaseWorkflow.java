package com.example.globalnewsenginev1.stories.snapshot;

import com.example.globalnewsenginev1.stories.embedding.StoryEmbeddingService;
import com.example.globalnewsenginev1.stories.query.StoryQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class StoryReleaseWorkflow {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryReleaseWorkflow.class);

    private final JdbcTemplate jdbc;
    private final StoryEmbeddingService embeddings;
    private final StorySnapshotService snapshots;
    private final StoryPromotionService promotion;
    private final StoryRegressionEvaluation regressions;
    private final StoryQueryService stories;
    private final ObjectMapper json;

    StoryReleaseWorkflow(JdbcTemplate jdbc, StoryEmbeddingService embeddings,
                         StorySnapshotService snapshots, StoryPromotionService promotion,
                         StoryRegressionEvaluation regressions, StoryQueryService stories) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.snapshots = snapshots;
        this.promotion = promotion;
        this.regressions = regressions;
        this.stories = stories;
        this.json = objectMapper();
    }

    public Evidence run(Request request) {
        long started = System.nanoTime();
        try {
            return run(request, started);
        } catch (RuntimeException exception) {
            progress("blocked", Map.of("error", exception.getClass().getSimpleName(),
                    "message", exception.getMessage() == null ? "Release failed" : exception.getMessage()), started);
            throw exception;
        }
    }

    private Evidence run(Request request, long started) {
        requireText(request.review().reviewedBy(), "Reviewer");
        requireText(request.review().document(), "Review document");
        requireText(request.review().reason(), "Review reason");
        Precheck before = precheck(request.candidateVersion());
        progress("precheck", before, started);
        if (request.promote() && "ACTIVE".equals(before.candidateStatus())) {
            if (!Files.isRegularFile(request.evidenceFile())) {
                throw new IllegalStateException("ACTIVE candidate has no release evidence: " + request.evidenceFile());
            }
            Evidence existing = read(request.evidenceFile(), request);
            Verification verification = verifyPromotion(before.candidateVersionId());
            Evidence evidence = new Evidence(existing.candidateVersion(), existing.initialPrecheck(),
                    existing.finalPrecheck(), existing.holdout(), existing.reviewMetadata(), existing.review(),
                    existing.regression(), "ACTIVE", verification);
            write(request.evidenceFile(), request, evidence);
            progress("completed", Map.of("outcome", evidence.outcome(),
                    "evidence", request.evidenceFile().toString(), "reused", true), started);
            return evidence;
        }
        if (!"SHADOW".equals(before.candidateStatus())) {
            throw new IllegalStateException("Candidate must be SHADOW, but is " + before.candidateStatus());
        }
        if (before.activeVersionId() == null || before.targetWatermark() == null) {
            throw new IllegalStateException("An ACTIVE version with a published snapshot is required");
        }
        if (before.processingUnfinished()) {
            throw new IllegalStateException("Candidate processing is already unfinished");
        }

        int previousOpen = before.missingInputs() + before.staleInputs() + before.incompleteEmbeddings();
        for (int batch = 1; batch <= request.maxEmbeddingBatches() && previousOpen > 0; batch++) {
            StoryEmbeddingService.ProcessingResult result =
                    embeddings.processVersion(before.candidateVersionId(), request.embeddingBatchSize(),
                            before.targetWatermark());
            Precheck current = precheck(request.candidateVersion());
            progress("embedding-batch", Map.of("batch", batch, "result", result,
                    "missingInputs", current.missingInputs(),
                    "staleInputs", current.staleInputs(),
                    "incompleteEmbeddings", current.incompleteEmbeddings()), started);
            int open = current.missingInputs() + current.staleInputs() + current.incompleteEmbeddings();
            if (open >= previousOpen || result.failed() > 0) break;
            previousOpen = open;
        }

        Precheck prepared = precheck(request.candidateVersion());
        if (prepared.missingInputs() > 0 || prepared.staleInputs() > 0
                || prepared.incompleteEmbeddings() > 0) {
            throw new IllegalStateException("Candidate preparation is incomplete: missingInputs="
                    + prepared.missingInputs() + ", staleInputs=" + prepared.staleInputs()
                    + ", incompleteEmbeddings=" + prepared.incompleteEmbeddings());
        }
        StorySnapshotService.ProcessingResult snapshot = snapshots.processBackfill(
                prepared.candidateVersionId(), prepared.targetWatermark());
        progress("snapshot", snapshot, started);

        Precheck after = precheck(request.candidateVersion());
        StoryPromotionService.Review review = promotion.review(after.candidateVersionId(), request.holdout());
        StoryRegressionEvaluation.Result regression =
                regressions.evaluate(after.candidateVersionId(), request.regressionCorpus());
        String outcome = "REVIEWED";
        if (request.promote()) {
            if (!review.evaluation().passed()) {
                outcome = "BLOCKED_GATES";
            } else if (!complete(request.approval())) {
                outcome = "BLOCKED_APPROVAL";
            } else {
                Evidence pending = new Evidence(request.candidateVersion(), before, after, request.holdout(),
                        request.review(), review, regression, "PROMOTION_PENDING", null);
                write(request.evidenceFile(), request, pending);
                promotion.promote(review, request.holdout(), request.approval());
                Evidence active = new Evidence(request.candidateVersion(), before, after, request.holdout(),
                        request.review(), review, regression, "ACTIVE",
                        verifyPromotion(after.candidateVersionId()));
                write(request.evidenceFile(), request, active);
                progress("completed", Map.of("outcome", active.outcome(),
                        "evidence", request.evidenceFile().toString()), started);
                return active;
            }
        }
        Evidence evidence = new Evidence(request.candidateVersion(), before, after, request.holdout(), request.review(),
                review, regression, outcome, null);
        write(request.evidenceFile(), request, evidence);
        progress("completed", Map.of("outcome", outcome, "evidence", request.evidenceFile().toString()), started);
        return evidence;
    }

    Precheck precheck(String versionKey) {
        Version candidate = jdbc.query("""
                SELECT id, status FROM story_clustering_versions WHERE version_key = ?
                """, (resultSet, rowNum) -> new Version(resultSet.getLong(1), resultSet.getString(2)), versionKey)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Unknown clustering version: " + versionKey));
        Version active = jdbc.query("""
                SELECT id, status FROM story_clustering_versions WHERE status = 'ACTIVE'
                """, (resultSet, rowNum) -> new Version(resultSet.getLong(1), resultSet.getString(2)))
                .stream().findFirst().orElse(null);
        Instant activeWatermark = active == null ? null : latestWatermark(active.id());
        Instant candidateWatermark = latestWatermark(candidate.id());
        Instant target = activeWatermark != null ? activeWatermark : candidateWatermark;
        int missing = target == null ? 0 : jdbc.queryForObject("""
                SELECT COUNT(*) FROM articles article
                WHERE article.first_seen_at <= ? AND NOT EXISTS (
                  SELECT 1 FROM story_article_inputs input
                  WHERE input.clustering_version_id = ? AND input.article_id = article.id
                    AND input.current_marker = 1)
                """, Integer.class, Timestamp.from(target), candidate.id());
        int incomplete = target == null ? 0 : jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_article_inputs
                WHERE clustering_version_id = ? AND current_marker = 1
                  AND effective_at <= ? AND input_disposition = 'INCLUDE'
                  AND embedding_status NOT IN ('READY', 'NOT_REQUIRED')
                """, Integer.class, candidate.id(), Timestamp.from(target));
        int stale = target == null ? 0 : jdbc.queryForObject("""
                SELECT COUNT(*) FROM articles article
                JOIN story_article_inputs input ON input.article_id = article.id
                  AND input.clustering_version_id = ? AND input.current_marker = 1
                WHERE article.first_seen_at <= ? AND (
                  article.updated_at > input.created_at OR COALESCE(
                    (SELECT MAX(g.created_at) FROM gdelt_gkg g WHERE g.article_id = article.id),
                    article.created_at) > input.created_at)
                """, Integer.class, candidate.id(), Timestamp.from(target));
        boolean running = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM story_processing_runs
                WHERE clustering_version_id = ? AND status IN ('PENDING', 'RUNNING'))
                """, Boolean.class, candidate.id()));
        return new Precheck(candidate.id(), candidate.status(), active == null ? null : active.id(),
                activeWatermark, candidateWatermark, target,
                activeWatermark != null && (candidateWatermark == null || candidateWatermark.isBefore(activeWatermark)),
                missing, stale, incomplete, running);
    }

    private Instant latestWatermark(long versionId) {
        return jdbc.query("""
                SELECT snapshot.snapshot_watermark FROM story_publish_commits commit
                JOIN story_snapshots snapshot ON snapshot.id = commit.snapshot_id
                WHERE commit.clustering_version_id = ?
                ORDER BY snapshot.snapshot_watermark DESC, commit.id DESC LIMIT 1
                """, (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant(), versionId)
                .stream().findFirst().orElse(null);
    }

    private Verification verifyPromotion(long candidateId) {
        List<Long> active = jdbc.queryForList(
                "SELECT id FROM story_clustering_versions WHERE status = 'ACTIVE'", Long.class);
        if (!active.equals(List.of(candidateId))) {
            throw new IllegalStateException("Promotion did not leave exactly the candidate ACTIVE");
        }
        var page = stories.stories(0, 1);
        if (page.stories().isEmpty() || stories.story(page.stories().getFirst().id()).isEmpty()) {
            throw new IllegalStateException("Story list or detail verification failed after promotion");
        }
        return new Verification(active.size(), page.total(), page.stories().getFirst().id().toString());
    }

    void write(Path path, Request request, Evidence evidence) {
        Path temporary = null;
        try {
            Path target = path.toAbsolutePath();
            Path parent = target.getParent();
            Files.createDirectories(parent);
            byte[] payload = json.writeValueAsBytes(evidence);
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("schemaVersion", "story-release-evidence-v2");
            document.put("requestHash", requestHash(request));
            document.put("evidenceHash", StoryReleaseEvaluation.hash(new String(payload, java.nio.charset.StandardCharsets.UTF_8)));
            document.put("evidence", evidence);
            temporary = Files.createTempFile(parent, "." + target.getFileName(), ".tmp");
            json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), document);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write release evidence to " + path, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The target evidence has already been written or the original exception is more useful.
                }
            }
        }
    }

    private Evidence read(Path path, Request request) {
        try {
            JsonNode document = json.readTree(path.toFile());
            if (!"story-release-evidence-v2".equals(document.path("schemaVersion").asText())) {
                throw new IllegalStateException("Existing release evidence has an unsupported schema: " + path);
            }
            if (!requestHash(request).equals(document.path("requestHash").asText())) {
                throw new IllegalStateException("Existing release evidence inputs differ from this request");
            }
            JsonNode payload = document.path("evidence");
            String actualHash = StoryReleaseEvaluation.hash(json.writeValueAsString(payload));
            if (!actualHash.equals(document.path("evidenceHash").asText())) {
                throw new IllegalStateException("Existing release evidence hash is invalid: " + path);
            }
            Evidence evidence = json.treeToValue(payload, Evidence.class);
            if (!request.candidateVersion().equals(evidence.candidateVersion())) {
                throw new IllegalStateException("Existing release evidence belongs to another candidate");
            }
            return evidence;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read existing release evidence from " + path, exception);
        }
    }

    private String requestHash(Request request) {
        try {
            Map<String, Object> identity = new LinkedHashMap<>();
            identity.put("candidateVersion", request.candidateVersion());
            identity.put("holdout", request.holdout());
            identity.put("regressionCorpus", request.regressionCorpus());
            identity.put("review", request.review());
            identity.put("promote", request.promote());
            identity.put("approval", request.approval());
            identity.put("embeddingBatchSize", request.embeddingBatchSize());
            identity.put("maxEmbeddingBatches", request.maxEmbeddingBatches());
            return StoryReleaseEvaluation.hash(json.writeValueAsString(identity));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot hash release request", exception);
        }
    }

    private boolean complete(StoryPromotionService.Approval approval) {
        return approval != null && approval.approvedBy() != null && !approval.approvedBy().isBlank()
                && approval.document() != null && !approval.document().isBlank()
                && approval.reason() != null && !approval.reason().isBlank();
    }

    private void progress(String step, Object value, long started) {
        try {
            LOGGER.info("{}", json.writeValueAsString(Map.of("storyReleaseStep", step, "data", value,
                    "elapsedMillis", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot serialize release progress", exception);
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    static ObjectMapper objectMapper() {
        SimpleModule time = new SimpleModule();
        time.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override
            public void serialize(Instant value, com.fasterxml.jackson.core.JsonGenerator generator,
                                  com.fasterxml.jackson.databind.SerializerProvider serializers)
                    throws IOException {
                generator.writeString(value.toString());
            }
        });
        time.addDeserializer(Instant.class, new JsonDeserializer<>() {
            @Override
            public Instant deserialize(com.fasterxml.jackson.core.JsonParser parser,
                                       com.fasterxml.jackson.databind.DeserializationContext context)
                    throws IOException {
                return Instant.parse(parser.getValueAsString());
            }
        });
        return new ObjectMapper().registerModule(time);
    }

    public record Request(String candidateVersion, StoryReleaseEvaluation.Holdout holdout,
                          JsonNode regressionCorpus, ReviewMetadata review, boolean promote,
                          StoryPromotionService.Approval approval, Path evidenceFile,
                          int embeddingBatchSize, int maxEmbeddingBatches) { }
    public record ReviewMetadata(String reviewedBy, String document, String reason) { }
    public record Precheck(long candidateVersionId, String candidateStatus, Long activeVersionId,
                           Instant activeWatermark, Instant candidateWatermark, Instant targetWatermark,
                           boolean candidateOlder, int missingInputs, int staleInputs, int incompleteEmbeddings,
                           boolean processingUnfinished) { }
    public record Verification(int activeVersions, long visibleStories, String detailStoryId) { }
    public record Evidence(String candidateVersion, Precheck initialPrecheck, Precheck finalPrecheck,
                           StoryReleaseEvaluation.Holdout holdout, ReviewMetadata reviewMetadata,
                           StoryPromotionService.Review review,
                           StoryRegressionEvaluation.Result regression, String outcome,
                           Verification verification) { }
    private record Version(long id, String status) { }
}
