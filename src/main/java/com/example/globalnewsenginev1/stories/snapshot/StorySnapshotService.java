package com.example.globalnewsenginev1.stories.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class StorySnapshotService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorySnapshotService.class);
    private static final String EXACT_SEARCH_MODE = "exact-cosine-radius-v1";
    private static final Set<Integer> SUPPORTED_WINDOWS = Set.of(24, 48, 72);
    private static final BigDecimal SUPPORTED_THRESHOLD = new BigDecimal("0.700000");

    private final StorySnapshotRepository repository;
    private final TransactionTemplate snapshotTransaction;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration claimTimeout;
    private final StorySnapshotMetrics metrics;

    @Autowired
    StorySnapshotService(
            StorySnapshotRepository repository,
            PlatformTransactionManager transactionManager,
            @Value("${stories.snapshots.claim-timeout:PT30M}") Duration claimTimeout,
            StorySnapshotMetrics metrics
    ) {
        this(repository, transactionManager, Clock.systemUTC(), claimTimeout, metrics);
    }

    StorySnapshotService(
            StorySnapshotRepository repository,
            PlatformTransactionManager transactionManager,
            Clock clock,
            Duration claimTimeout,
            StorySnapshotMetrics metrics
    ) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.snapshotTransaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.claimTimeout = claimTimeout;
        this.metrics = metrics;
    }

    public ProcessingResult processIncremental(int maxVersions) {
        return process(RunMode.INCREMENTAL, clock.instant(), maxVersions);
    }

    public ProcessingResult processBackfill(Instant watermark, int maxVersions) {
        return process(RunMode.BACKFILL, watermark, maxVersions);
    }

    public ProcessingResult reprocess(Instant watermark, int maxVersions) {
        return process(RunMode.REPROCESSING, watermark, maxVersions);
    }

    ProcessingResult process(RunMode mode, Instant requestedWatermark, int maxVersions) {
        if (maxVersions <= 0) {
            throw new IllegalArgumentException("maxVersions must be positive");
        }
        Instant watermark = StorySnapshotCanonicalizer.normalizeWatermark(requestedWatermark);
        List<StorySnapshotRepository.ClusteringVersion> versions =
                repository.findShadowVersions(maxVersions);
        int succeeded = 0;
        int reused = 0;
        int failed = 0;
        for (StorySnapshotRepository.ClusteringVersion version : versions) {
            long started = System.nanoTime();
            try {
                VersionResult result = processVersion(version, mode, watermark);
                if (result == VersionResult.SUCCEEDED) {
                    succeeded++;
                } else {
                    reused++;
                }
            } catch (RuntimeException exception) {
                failed++;
                metrics.failure();
                LOGGER.error("Story snapshot processing failed for version={} watermark={}",
                        version.key(), watermark, exception);
            } finally {
                metrics.latency(System.nanoTime() - started);
            }
        }
        return new ProcessingResult(versions.size(), succeeded, reused, failed);
    }

    private VersionResult processVersion(
            StorySnapshotRepository.ClusteringVersion version,
            RunMode mode,
            Instant watermark
    ) {
        validateVersion(version);
        Instant now = StorySnapshotCanonicalizer.normalizeWatermark(clock.instant());
        Optional<StorySnapshotRepository.RetrySnapshot> retry =
                repository.findRetryableSnapshot(
                        version.id(), mode, now, claimTimeout);
        SnapshotWork work;
        if (retry.isPresent()) {
            work = new SnapshotWork(
                    retry.get().snapshot(), retry.get().memberCount());
        } else {
            work = snapshotTransaction.execute(status -> {
                repository.lockVersion(version.id());
                List<StorySnapshotRepository.SnapshotInput> inputs =
                        repository.findReadyInputs(version.id(), watermark);
                String inputHash = StorySnapshotCanonicalizer.snapshotInputHash(
                        version.key(), watermark, inputs);
                String snapshotKey = StorySnapshotCanonicalizer.snapshotKey(
                        version.key(), watermark, inputHash);
                StorySnapshotRepository.Snapshot snapshot = repository.ensureSnapshot(
                        version, watermark, snapshotKey, inputHash, inputs,
                        StorySnapshotCanonicalizer.normalizeWatermark(clock.instant()));
                return new SnapshotWork(snapshot, inputs.size());
            });
        }
        if (work == null) {
            throw new IllegalStateException("Snapshot transaction returned no result");
        }
        metrics.snapshot(work.snapshot().created(), work.memberCount());

        String runKey = StorySnapshotCanonicalizer.runKey(
                version.key(), work.snapshot().inputHash(), mode, version.pairRuleVersion());
        Instant claimTime = StorySnapshotCanonicalizer.normalizeWatermark(clock.instant());
        Optional<StorySnapshotRepository.RunClaim> claim = transaction.execute(status ->
                repository.claimRun(version, work.snapshot(), mode, runKey,
                        claimTime, claimTimeout));
        if (claim == null || claim.isEmpty()) {
            return VersionResult.REUSED;
        }

        try {
            List<StorySnapshotRepository.SnapshotInput> frozenInputs =
                    repository.loadSnapshotInputs(work.snapshot().id());
            PairSearchResult search = search(version, frozenInputs);
            transaction.executeWithoutResult(status -> {
                Instant completedAt =
                        StorySnapshotCanonicalizer.normalizeWatermark(clock.instant());
                for (PairDecision decision : search.decisions()) {
                    String decisionHash = StorySnapshotCanonicalizer.decisionHash(
                            work.snapshot(), decision.left(), decision.right(),
                            decision, version.pairRuleVersion());
                    repository.insertDecision(version, work.snapshot(), claim.get(),
                            decision, decisionHash, completedAt);
                }
                repository.completeRun(claim.get(), frozenInputs.size(),
                        search.changedArticles(), frozenInputs.size() - search.changedArticles(),
                        search.comparedPairs(), completedAt);
            });
            long sameStory = search.decisions().stream()
                    .filter(decision -> "SAME_STORY".equals(decision.result())).count();
            metrics.results(search.comparedPairs(), sameStory,
                    search.decisions().size() - sameStory);
            return VersionResult.SUCCEEDED;
        } catch (RuntimeException exception) {
            transaction.executeWithoutResult(status -> repository.failRun(
                    claim.get(), 1,
                    StorySnapshotCanonicalizer.normalizeWatermark(clock.instant())));
            throw exception;
        }
    }

    private PairSearchResult search(
            StorySnapshotRepository.ClusteringVersion version,
            List<StorySnapshotRepository.SnapshotInput> inputs
    ) {
        Map<Long, float[]> vectors = new HashMap<>();
        for (StorySnapshotRepository.SnapshotInput input : inputs) {
            if (input.embeddingDimension() != version.dimension()) {
                throw new ExactCosine.InvalidVectorException("DIMENSION_MISMATCH",
                        "Artifact dimension differs from clustering version for "
                                + input.articleRef());
            }
            vectors.put(input.articleInputId(), ExactCosine.decode(
                    input.vectorBytes(), version.dimension(), input.vectorHash()));
        }

        List<PairDecision> sameStory = new ArrayList<>();
        Map<String, BestComparison> bestComparison = new HashMap<>();
        Set<String> articlesWithPositive = new HashSet<>();
        long comparedPairs = 0;
        Duration window = Duration.ofHours(version.windowHours());

        for (int leftIndex = 0; leftIndex < inputs.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < inputs.size(); rightIndex++) {
                StorySnapshotRepository.SnapshotInput first = inputs.get(leftIndex);
                StorySnapshotRepository.SnapshotInput second = inputs.get(rightIndex);
                Duration distance = Duration.between(
                        first.effectiveAt(), second.effectiveAt()).abs();
                if (distance.compareTo(window) > 0) {
                    continue;
                }
                comparedPairs++;
                StorySnapshotRepository.SnapshotInput left =
                        first.articleRef().compareTo(second.articleRef()) < 0 ? first : second;
                StorySnapshotRepository.SnapshotInput right = left == first ? second : first;
                ExactCosine.Score score = ExactCosine.score(
                        vectors.get(left.articleInputId()),
                        vectors.get(right.articleInputId()));
                BigDecimal similarity = score.quantized();
                updateBest(bestComparison, first, second, score);
                updateBest(bestComparison, second, first, score);
                if (similarity.compareTo(version.threshold()) >= 0) {
                    articlesWithPositive.add(first.articleRef());
                    articlesWithPositive.add(second.articleRef());
                    sameStory.add(new PairDecision(left, right, similarity,
                            distance.toSeconds(), 0, "SAME_STORY",
                            version.pairRuleVersion(), false));
                }
            }
        }

        Map<PairKey, PairDecision> decisions = new LinkedHashMap<>();
        for (PairDecision decision : sameStory) {
            decisions.put(new PairKey(
                    decision.left().articleRef(), decision.right().articleRef()), decision);
        }
        for (StorySnapshotRepository.SnapshotInput input : inputs) {
            if (articlesWithPositive.contains(input.articleRef())) {
                continue;
            }
            BestComparison best = bestComparison.get(input.articleRef());
            if (best == null) {
                continue;
            }
            StorySnapshotRepository.SnapshotInput left =
                    input.articleRef().compareTo(best.other().articleRef()) < 0
                            ? input : best.other();
            StorySnapshotRepository.SnapshotInput right = left == input ? best.other() : input;
            long distance = Duration.between(
                    left.effectiveAt(), right.effectiveAt()).abs().toSeconds();
            decisions.putIfAbsent(new PairKey(left.articleRef(), right.articleRef()),
                    new PairDecision(left, right, best.similarity(), distance, 0,
                            "UNCERTAIN", "top-one-below-threshold-v1", true));
        }

        Comparator<PairDecision> order = Comparator
                .comparing(PairDecision::similarity, Comparator.reverseOrder())
                .thenComparing(decision -> decision.left().articleRef())
                .thenComparing(decision -> decision.right().articleRef());
        List<PairDecision> ordered = decisions.values().stream().sorted(order).toList();
        List<PairDecision> ranked = new ArrayList<>(ordered.size());
        Set<String> changedArticles = new HashSet<>();
        for (int index = 0; index < ordered.size(); index++) {
            PairDecision decision = ordered.get(index);
            ranked.add(new PairDecision(decision.left(), decision.right(),
                    decision.similarity(), decision.timeDistanceSeconds(), index + 1,
                    decision.result(), decision.triggeredRule(),
                    decision.topOneBelowThreshold()));
            changedArticles.add(decision.left().articleRef());
            changedArticles.add(decision.right().articleRef());
        }
        return new PairSearchResult(List.copyOf(ranked), comparedPairs,
                changedArticles.size());
    }

    private void updateBest(
            Map<String, BestComparison> comparisons,
            StorySnapshotRepository.SnapshotInput article,
            StorySnapshotRepository.SnapshotInput other,
            ExactCosine.Score score
    ) {
        BestComparison candidate = new BestComparison(other, score.exact(), score.quantized());
        comparisons.merge(article.articleRef(), candidate, (current, replacement) -> {
            int scoreOrder = Double.compare(replacement.exactSimilarity(),
                    current.exactSimilarity());
            if (scoreOrder > 0
                    || (scoreOrder == 0 && replacement.other().articleRef()
                    .compareTo(current.other().articleRef()) < 0)) {
                return replacement;
            }
            return current;
        });
    }

    private void validateVersion(StorySnapshotRepository.ClusteringVersion version) {
        if (!EXACT_SEARCH_MODE.equals(version.searchMode())) {
            throw new IllegalArgumentException(
                    "Unsupported candidate search mode: " + version.searchMode());
        }
        if (!SUPPORTED_WINDOWS.contains(version.windowHours())) {
            throw new IllegalArgumentException(
                    "Unsupported candidate window: " + version.windowHours());
        }
        if (version.threshold().compareTo(SUPPORTED_THRESHOLD) != 0) {
            throw new IllegalArgumentException(
                    "Unsupported candidate threshold: " + version.threshold());
        }
    }

    public enum RunMode {
        INCREMENTAL,
        BACKFILL,
        REPROCESSING
    }

    public record ProcessingResult(
            int selectedVersions,
            int succeededVersions,
            int reusedVersions,
            int failedVersions
    ) {
    }

    record PairDecision(
            StorySnapshotRepository.SnapshotInput left,
            StorySnapshotRepository.SnapshotInput right,
            BigDecimal similarity,
            long timeDistanceSeconds,
            int rank,
            String result,
            String triggeredRule,
            boolean topOneBelowThreshold
    ) {
    }

    private enum VersionResult {
        SUCCEEDED,
        REUSED
    }

    private record SnapshotWork(
            StorySnapshotRepository.Snapshot snapshot,
            int memberCount
    ) {
    }

    private record BestComparison(
            StorySnapshotRepository.SnapshotInput other,
            double exactSimilarity,
            BigDecimal similarity
    ) {
    }

    private record PairKey(String leftArticleRef, String rightArticleRef) {
    }

    private record PairSearchResult(
            List<PairDecision> decisions,
            long comparedPairs,
            int changedArticles
    ) {
    }
}
