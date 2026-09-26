package com.example.globalnewsenginev1.stories.snapshot;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StoryPartitionService {

    static final String COMPONENT_RULE = "medoid-radius-agglomerative-v1";
    private final StorySnapshotRepository repository;

    StoryPartitionService(StorySnapshotRepository repository) {
        this.repository = repository;
    }

    /** Reconstructs a partition from immutable snapshot artifacts without publishing it. */
    public Partition calculate(long snapshotId) {
        SnapshotRules rules = repository.loadPartitionRules(snapshotId);
        return partition(rules, repository.loadSnapshotInputs(snapshotId));
    }

    static Partition partitionForPublication(SnapshotRules rules,
            List<StorySnapshotRepository.SnapshotInput> inputs,
            List<StorySnapshotService.PairDecision> pairs) {
        if (inputs.isEmpty()) return partition(rules, inputs, false);
        Map<String, Integer> indexes = new HashMap<>();
        int[] parents = new int[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            indexes.put(inputs.get(i).articleRef(), i);
            parents[i] = i;
        }
        // Every possible medoid merge is an edge in the already computed exact candidate graph.
        // Disconnected groups cannot influence each other, even after medoids change.
        for (var pair : pairs) {
            if ("SAME_STORY".equals(pair.result())) {
                int left = root(parents, indexes.get(pair.left().articleRef()));
                int right = root(parents, indexes.get(pair.right().articleRef()));
                parents[Math.max(left, right)] = Math.min(left, right);
            }
        }
        Map<Integer, List<StorySnapshotRepository.SnapshotInput>> groups = new LinkedHashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            groups.computeIfAbsent(root(parents, i), ignored -> new ArrayList<>()).add(inputs.get(i));
        }
        List<Component> components = new ArrayList<>();
        for (var group : groups.values()) {
            components.addAll(partition(rules, group, false).components());
        }
        components.sort(Comparator
                .comparing((Component c) -> inputs.get(indexes.get(c.members().getFirst().articleRef())).effectiveAt())
                .thenComparing(c -> c.members().getFirst().articleRef()));
        return new Partition(rules, List.copyOf(components), List.of());
    }

    private static int root(int[] parents, int index) {
        while (parents[index] != index) {
            parents[index] = parents[parents[index]];
            index = parents[index];
        }
        return index;
    }

    static Partition partition(SnapshotRules rules,
                               List<StorySnapshotRepository.SnapshotInput> frozenInputs) {
        return partition(rules, frozenInputs, true);
    }

    static Partition partition(SnapshotRules rules,
                               List<StorySnapshotRepository.SnapshotInput> frozenInputs,
                               boolean includeMergeDiagnostics) {
        if (!COMPONENT_RULE.equals(rules.componentRuleVersion())
                || !"exact-cosine-radius-v1".equals(rules.searchMode())
                || !Set.of(24, 48, 72).contains(rules.windowHours())
                || rules.threshold().compareTo(new BigDecimal("0.700000")) != 0
                || rules.dimension() <= 0) {
            throw new IllegalArgumentException("Unsupported clustering version: " + rules.versionKey());
        }
        List<StorySnapshotRepository.SnapshotInput> inputs = frozenInputs.stream()
                .sorted(Comparator.comparing(StorySnapshotRepository.SnapshotInput::effectiveAt)
                        .thenComparing(StorySnapshotRepository.SnapshotInput::articleRef)).toList();
        Set<String> refs = new HashSet<>();
        List<float[]> vectors = new ArrayList<>();
        List<Cluster> clusters = new ArrayList<>();
        for (var input : inputs) {
            if (!refs.add(input.articleRef())) {
                throw new IllegalArgumentException("Duplicate snapshot article: " + input.articleRef());
            }
            if (input.embeddingDimension() != rules.dimension()) {
                throw new ExactCosine.InvalidVectorException("DIMENSION_MISMATCH",
                        "Artifact dimension differs from clustering version");
            }
            vectors.add(ExactCosine.decode(input.vectorBytes(), rules.dimension(), input.vectorHash()));
            int index = clusters.size();
            clusters.add(new Cluster(List.of(index), index));
        }
        Scores scores = new Scores(vectors);
        Duration window = Duration.ofHours(rules.windowHours());
        long threshold = rules.threshold().movePointRight(6).longValueExact();
        Set<Candidate> rejected = new HashSet<>();
        List<MergeDecision> decisions = new ArrayList<>();
        Comparator<Candidate> order = Comparator.comparingLong(Candidate::similarity).reversed()
                .thenComparing(c -> inputs.get(c.left().medoid()).articleRef())
                .thenComparing(c -> inputs.get(c.right().medoid()).articleRef());
        // ponytail: rescan cluster pairs after each merge; use an invalidating priority queue
        // if measured snapshot sizes make these O(n^3) pair scans too slow.
        while (true) {
            Candidate best = null;
            for (int i = 0; i < clusters.size(); i++) {
                for (int j = i + 1; j < clusters.size(); j++) {
                    Cluster left = clusters.get(i);
                    Cluster right = clusters.get(j);
                    if (inputs.get(left.medoid()).articleRef()
                            .compareTo(inputs.get(right.medoid()).articleRef()) > 0) {
                        Cluster swap = left;
                        left = right;
                        right = swap;
                    }
                    if (distance(inputs, left.medoid(), right.medoid()).compareTo(window) > 0) {
                        continue;
                    }
                    long score = scores.get(left.medoid(), right.medoid());
                    Candidate candidate = new Candidate(left, right, score);
                    if (score >= threshold && !rejected.contains(candidate)
                            && (best == null || order.compare(candidate, best) < 0)) {
                        best = candidate;
                    }
                }
            }
            if (best == null) break;
            Candidate candidate = best;
            List<Integer> members = new ArrayList<>(candidate.left().members());
            members.addAll(candidate.right().members());
            members.sort(Integer::compareTo);
            int medoid = medoid(members, scores);
            List<MemberEvidence> evidence = evidence(members, medoid, inputs, scores);
            boolean accepted = evidence.stream().allMatch(member ->
                    member.similarityToMedoid().compareTo(rules.threshold()) >= 0
                            && member.timeDistance().compareTo(window) <= 0);
            if (includeMergeDiagnostics) decisions.add(new MergeDecision(refs(candidate.left(), inputs),
                    refs(candidate.right(), inputs), decimal(candidate.similarity()),
                    inputs.get(medoid).articleRef(), accepted, evidence));
            if (accepted) {
                clusters.remove(candidate.left());
                clusters.remove(candidate.right());
                clusters.add(new Cluster(List.copyOf(members), medoid));
                clusters.sort(Comparator.comparingInt(c -> c.members().getFirst()));
                rejected.removeIf(previous -> previous.left().equals(candidate.left())
                        || previous.right().equals(candidate.left())
                        || previous.left().equals(candidate.right())
                        || previous.right().equals(candidate.right()));
            } else {
                rejected.add(candidate);
            }
        }
        List<Component> components = clusters.stream().map(cluster -> new Component(
                inputs.get(cluster.medoid()).articleRef(),
                evidence(cluster.members(), cluster.medoid(), inputs, scores))).toList();
        return new Partition(rules, components, List.copyOf(decisions));
    }

    private static int medoid(List<Integer> members, Scores scores) {
        int best = members.getFirst();
        long bestSum = Long.MIN_VALUE;
        for (int member : members) {
            long sum = 0;
            for (int other : members) {
                if (member != other) {
                    sum += scores.get(member, other);
                }
            }
            // The denominator is identical for every member; sorted members resolve ties.
            if (sum > bestSum) {
                best = member;
                bestSum = sum;
            }
        }
        return best;
    }

    private static List<String> refs(Cluster cluster,
                                    List<StorySnapshotRepository.SnapshotInput> inputs) {
        return cluster.members().stream().map(index -> inputs.get(index).articleRef()).toList();
    }

    private static List<MemberEvidence> evidence(List<Integer> members, int medoid,
                                               List<StorySnapshotRepository.SnapshotInput> inputs,
                                               Scores scores) {
        return members.stream().map(index -> {
            var input = inputs.get(index);
            return new MemberEvidence(input.articleRef(), input.articleInputFingerprint(),
                    input.embeddingArtifactId(), input.vectorHash(),
                    decimal(scores.get(index, medoid)), distance(inputs, index, medoid));
        }).toList();
    }

    private static Duration distance(List<StorySnapshotRepository.SnapshotInput> inputs,
                                     int left, int right) {
        return Duration.between(inputs.get(left).effectiveAt(), inputs.get(right).effectiveAt()).abs();
    }

    private static BigDecimal decimal(long score) {
        return BigDecimal.valueOf(score, 6);
    }

    private record Cluster(List<Integer> members, int medoid) { }
    private record Candidate(Cluster left, Cluster right, long similarity) { }

    private static final class Scores {
        private final List<float[]> vectors;
        // Eviction only causes exact recomputation; it never changes scores or the partition.
        private final Map<Long, Long> cache = new LinkedHashMap<>(1024, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
                return size() > 65_536;
            }
        };

        Scores(List<float[]> vectors) {
            this.vectors = vectors;
        }

        long get(int left, int right) {
            if (left == right) {
                return 1_000_000;
            }
            int first = Math.min(left, right);
            int second = Math.max(left, right);
            long key = ((long) first << 32) | second;
            return cache.computeIfAbsent(key, ignored -> ExactCosine
                    .similarity(vectors.get(first), vectors.get(second))
                    .movePointRight(6).longValueExact());
        }
    }

    public record SnapshotRules(long snapshotId, String snapshotKey, String snapshotInputHash,
                                String versionKey, int dimension, int windowHours,
                                BigDecimal threshold, String searchMode,
                                String componentRuleVersion) { }

    public record MemberEvidence(String articleRef, String articleInputFingerprint,
                                 long embeddingArtifactId, String vectorHash,
                                 BigDecimal similarityToMedoid, Duration timeDistance) { }

    public record Component(String medoidArticleRef, List<MemberEvidence> members) { }

    public record MergeDecision(List<String> leftArticleRefs, List<String> rightArticleRefs,
                                BigDecimal medoidSimilarity, String proposedMedoidArticleRef,
                                boolean accepted, List<MemberEvidence> members) { }

    public record Partition(SnapshotRules snapshot, List<Component> components,
                            List<MergeDecision> decisions) { }
}
