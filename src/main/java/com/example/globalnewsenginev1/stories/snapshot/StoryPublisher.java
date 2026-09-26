package com.example.globalnewsenginev1.stories.snapshot;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Publishes inside the caller's transaction, including its pair decisions and run completion. */
@Service
class StoryPublisher {
    private final JdbcTemplate jdbc;
    private final StorySnapshotRepository repository;
    private final TransactionTemplate transaction;
    private final MeterRegistry metrics;

    StoryPublisher(JdbcTemplate jdbc, StorySnapshotRepository repository,
                   PlatformTransactionManager manager, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.transaction = new TransactionTemplate(manager);
        this.metrics = metrics;
    }

    Plan prepare(StorySnapshotRepository.Snapshot snapshot) {
        return prepare(snapshot, StoryPartitionService.partition(repository.loadPartitionRules(snapshot.id()),
                repository.loadSnapshotInputs(snapshot.id()), false));
    }

    Plan prepare(StorySnapshotRepository.Snapshot snapshot,
                 StoryPartitionService.Partition partition) {
        // Read the identity basis consistently; expensive clustering does not hold the publisher lock.
        Basis basis = transaction.execute(status -> {
            repository.lockVersion(snapshot.versionId());
            return new Basis(stories(snapshot.versionId()), memberships(snapshot.versionId()),
                    latestCommit(snapshot.versionId()));
        });
        List<Input> inputs = jdbc.query("""
                SELECT input.id, input.article_id, member.article_ref,
                       member.article_input_fingerprint, input.effective_at, input.created_at,
                       input.title_usability
                FROM story_snapshot_members member
                JOIN story_article_inputs input ON input.id = member.article_input_id
                WHERE member.snapshot_id = ? ORDER BY input.effective_at, member.article_ref
                """, (rs, row) -> new Input(rs.getLong("id"), rs.getLong("article_id"),
                rs.getString("article_ref"), rs.getString("article_input_fingerprint"),
                rs.getTimestamp("effective_at").toInstant(), rs.getTimestamp("created_at").toInstant(),
                rs.getString("title_usability")), snapshot.id());
        return new Plan(snapshot, basis, inputs, partition);
    }

    Result publish(Plan plan, StorySnapshotRepository.RunClaim run, Instant now, Duration lease) {
        var snapshot = plan.snapshot();
        repository.lockVersion(snapshot.versionId());
        assertLease(snapshot.versionId(), run, now, lease);
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM story_publish_commits
                    WHERE clustering_version_id = ? AND snapshot_id = ?)
                """, Boolean.class, snapshot.versionId(), snapshot.id()))) {
            return new Result("REUSED", 0, plan.inputs().size(), Map.of());
        }
        boolean stale = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM story_publish_commits commit
                    JOIN story_snapshots snapshot ON snapshot.id = commit.snapshot_id
                    WHERE commit.clustering_version_id = ?
                      AND (snapshot.snapshot_watermark > ?
                        OR (snapshot.snapshot_watermark = ? AND snapshot.id > ?)))
                """, Boolean.class, snapshot.versionId(), ts(snapshot.watermark()),
                ts(snapshot.watermark()), snapshot.id()));
        if (stale) {
            return new Result("DISCARDED", 0, plan.inputs().size(), Map.of());
        }
        if (latestCommit(snapshot.versionId()) != plan.basis().commitId()
                || !stories(snapshot.versionId()).equals(plan.basis().stories())
                || !memberships(snapshot.versionId()).equals(plan.basis().memberships())) {
            throw conflict("Published story basis changed during calculation");
        }

        Map<String, Input> inputs = new LinkedHashMap<>();
        plan.inputs().forEach(input -> inputs.put(input.ref(), input));
        Map<String, Membership> previous = new HashMap<>();
        plan.basis().memberships().forEach(member -> previous.put(member.ref(), member));
        List<StoryPartitionService.Component> components = plan.partition().components();
        Map<String, Integer> componentByArticle = new HashMap<>();
        for (int i = 0; i < components.size(); i++) {
            for (var member : components.get(i).members()) {
                componentByArticle.put(member.articleRef(), i);
            }
        }
        Map<Integer, List<Story>> nominees = new HashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Story story : plan.basis().stories()) {
            Map<Integer, Integer> overlap = new HashMap<>();
            for (Membership member : plan.basis().memberships()) {
                Integer component = componentByArticle.get(member.ref());
                if (member.storyId().equals(story.id()) && component != null) {
                    overlap.merge(component, 1, Integer::sum);
                }
            }
            if (overlap.size() > 1) count(counts, "split");
            Integer nomination = componentByArticle.get(story.anchor());
            if (nomination == null || !overlap.containsKey(nomination)) {
                nomination = overlap.entrySet().stream()
                        .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                                .thenComparing(Map.Entry.comparingByKey()))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
            }
            if (nomination != null) {
                nominees.computeIfAbsent(nomination, ignored -> new ArrayList<>()).add(story);
            }
        }
        Map<String, UUID> assignments = new HashMap<>();
        Map<String, StoryPartitionService.MemberEvidence> evidence = new HashMap<>();
        Map<String, String> medoids = new HashMap<>();
        List<UUID> surviving = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            var component = components.get(i);
            List<Story> candidates = nominees.getOrDefault(i, List.of());
            Story old = candidates.stream().min(Comparator.comparing(Story::createdAt)
                    .thenComparing(story -> story.id().toString())).orElse(null);
            String firstRef = component.members().getFirst().articleRef();
            UUID id = old == null ? UUID.nameUUIDFromBytes((snapshot.versionId() + ":"
                    + snapshot.inputHash() + ":" + firstRef).getBytes(StandardCharsets.UTF_8)) : old.id();
            surviving.add(id);
            for (var member : component.members()) {
                assignments.put(member.articleRef(), id);
                evidence.put(member.articleRef(), member);
                medoids.put(member.articleRef(), component.medoidArticleRef());
            }
            if (candidates.size() > 1) count(counts, "merge");
            if (component.members().size() == 1) count(counts, "singleton");
            Instant from = component.members().stream().map(m -> inputs.get(m.articleRef()).effectiveAt())
                    .min(Instant::compareTo).orElseThrow();
            Instant to = component.members().stream().map(m -> inputs.get(m.articleRef()).effectiveAt())
                    .max(Instant::compareTo).orElseThrow();
            boolean changed = old == null || component.members().stream().anyMatch(m -> {
                Membership before = previous.get(m.articleRef());
                return before == null || !before.storyId().equals(id)
                        || !before.fingerprint().equals(m.articleInputFingerprint());
            }) || plan.basis().memberships().stream().anyMatch(m -> m.storyId().equals(id)
                    && !Objects.equals(componentByArticle.get(m.ref()), componentByArticle.get(firstRef)))
                    || !old.representative().equals(component.medoidArticleRef())
                    || !old.from().equals(from) || !old.to().equals(to);
            String state = changed ? "ACTIVE" : old.state();
            if (!changed && "ACTIVE".equals(state)) {
                Instant lastEvent = component.members().stream().map(m -> inputs.get(m.articleRef()).createdAt())
                        .max(Instant::compareTo).orElse(old.updatedAt());
                if (old.updatedAt().isAfter(lastEvent)) lastEvent = old.updatedAt();
                if (!snapshot.watermark().isBefore(lastEvent.plus(Duration.ofHours(72)))) state = "CLOSED";
            }
            if (old == null) {
                jdbc.update("""
                        INSERT INTO stories (id, clustering_version_id, identity_anchor_article_ref,
                            representative_article_ref, state, effective_from, effective_to,
                            created_by_run_id, last_changed_by_run_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?)
                        """, id, snapshot.versionId(), firstRef, component.medoidArticleRef(),
                        ts(from), ts(to), run.id(), run.id(), ts(now), ts(now));
                count(counts, "new");
            } else if (changed || !state.equals(old.state())) {
                updateStory(old, component.medoidArticleRef(), state, from, to, run, now);
                if (!state.equals(old.state())) count(counts, "CLOSED".equals(state) ? "closed" : "reopened");
                if (changed && component.members().size() > plan.basis().memberships().stream()
                        .filter(m -> m.storyId().equals(id)).count()) count(counts, "expanded");
            }
        }
        for (Story old : plan.basis().stories()) {
            if (!surviving.contains(old.id())) {
                updateStory(old, old.representative(), "SUPERSEDED", old.from(), old.to(), run, now);
                count(counts, "superseded");
            }
        }

        int changed = 0;
        for (Input input : plan.inputs()) {
            UUID target = assignments.get(input.ref());
            Membership old = previous.get(input.ref());
            boolean same = old != null && old.storyId().equals(target)
                    && old.fingerprint().equals(input.fingerprint());
            String reason = target != null ? "MEDOID_RADIUS"
                    : input.effectiveAt().isAfter(snapshot.watermark()) ? "AFTER_WATERMARK"
                    : "USABLE".equals(input.usability()) ? "EMBEDDING_NOT_READY" : input.usability();
            if (target == null) count(counts, "unassigned." + reason);
            String result = target == null ? "UNASSIGNED" : same ? "NO_CHANGE" : "ASSIGNED";
            var memberEvidence = evidence.get(input.ref());
            long decision = jdbc.queryForObject("""
                    INSERT INTO story_assignment_decisions (decision_hash, clustering_version_id,
                        run_id, snapshot_id, article_input_id, article_ref, article_input_fingerprint,
                        previous_story_id, resulting_story_id, result, assignment_reason,
                        component_rule_version, best_comparison_article_ref, best_cosine_similarity,
                        created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, hash(snapshot.inputHash() + ":" + input.ref()), snapshot.versionId(),
                    run.id(), snapshot.id(), input.id(), input.ref(), input.fingerprint(),
                    old == null ? null : old.storyId(), target, result, reason,
                    plan.partition().snapshot().componentRuleVersion(), medoids.get(input.ref()),
                    memberEvidence == null ? null : memberEvidence.similarityToMedoid(), ts(now));
            if (!same && old != null) {
                jdbc.update("""
                        UPDATE story_memberships SET current_marker = NULL, valid_to_run_id = ?, ended_at = ?
                        WHERE id = ? AND current_marker = 1
                        """, run.id(), ts(now), old.id());
            }
            if (!same && target != null) {
                jdbc.update("""
                        INSERT INTO story_memberships (story_id, clustering_version_id, article_id,
                            article_ref, article_input_fingerprint, valid_from_run_id, decision_id,
                            assignment_reason, current_marker, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                        """, target, snapshot.versionId(), input.articleId(), input.ref(), input.fingerprint(),
                        run.id(), decision, reason, ts(now));
            }
            if (!same && (target != null || old != null)) changed++;
        }
        for (Membership old : plan.basis().memberships()) {
            if (!inputs.containsKey(old.ref())) {
                throw conflict("Snapshot omits a current member; create a fresh snapshot");
            }
        }
        jdbc.update("""
                INSERT INTO story_publish_commits (publish_key, clustering_version_id, snapshot_id,
                    run_id, fencing_token, published_at) VALUES (?, ?, ?, ?, ?, ?)
                """, hash(snapshot.versionId() + ":" + snapshot.inputHash()), snapshot.versionId(),
                snapshot.id(), run.id(), run.fencingToken(), ts(now));
        counts.put("memberships", (long) assignments.size());
        return new Result("PUBLISHED", changed, Math.max(0, inputs.size() - changed), counts);
    }

    void assertLease(long versionId, StorySnapshotRepository.RunClaim run, Instant now, Duration lease) {
        boolean valid = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM story_processing_runs run
                    WHERE run.id = ? AND run.clustering_version_id = ?
                      AND run.status = 'RUNNING' AND run.fencing_token = ? AND run.started_at > ?
                      AND run.fencing_token = (SELECT MAX(fencing_token) FROM story_processing_runs
                          WHERE clustering_version_id = ?))
                """, Boolean.class, run.id(), versionId, run.fencingToken(), ts(now.minus(lease)), versionId));
        if (!valid) throw conflict("Publisher lease expired or fencing token superseded");
    }

    void record(Result result) {
        metrics.counter("stories.publish.runs", "result", result.outcome()).increment();
        result.counts().forEach((name, count) ->
                metrics.counter("stories.publish.results", "kind", name).increment(count));
    }

    private IllegalStateException conflict(String message) {
        metrics.counter("stories.publish.conflicts").increment();
        return new IllegalStateException(message);
    }

    private void updateStory(Story old, String representative, String state, Instant from, Instant to,
                             StorySnapshotRepository.RunClaim run, Instant now) {
        int updated = jdbc.update("""
                UPDATE stories SET representative_article_ref = ?, state = ?, effective_from = ?,
                    effective_to = ?, last_changed_by_run_id = ?, updated_at = ?,
                    closed_at = ?, superseded_at = ?, optimistic_version = optimistic_version + 1
                WHERE id = ? AND optimistic_version = ?
                """, representative, state, ts(from), ts(to), run.id(), ts(now),
                "CLOSED".equals(state) ? ts(now) : null,
                "SUPERSEDED".equals(state) ? ts(now) : null, old.id(), old.version());
        if (updated != 1) throw conflict("Story optimistic version changed");
    }

    private List<Story> stories(long versionId) {
        return jdbc.query("""
                SELECT * FROM stories WHERE clustering_version_id = ? AND state <> 'SUPERSEDED'
                ORDER BY created_at, id FOR UPDATE
                """, (rs, row) -> new Story(rs.getObject("id", UUID.class),
                rs.getString("identity_anchor_article_ref"), rs.getString("representative_article_ref"),
                rs.getString("state"), rs.getTimestamp("effective_from").toInstant(),
                rs.getTimestamp("effective_to").toInstant(), rs.getLong("optimistic_version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), versionId);
    }

    private List<Membership> memberships(long versionId) {
        return jdbc.query("""
                SELECT id, story_id, article_ref, article_input_fingerprint FROM story_memberships
                WHERE clustering_version_id = ? AND current_marker = 1 ORDER BY article_ref
                """, (rs, row) -> new Membership(rs.getLong("id"), rs.getObject("story_id", UUID.class),
                rs.getString("article_ref"), rs.getString("article_input_fingerprint")), versionId);
    }

    private long latestCommit(long versionId) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM story_publish_commits WHERE clustering_version_id = ?",
                Long.class, versionId);
    }

    private static Timestamp ts(Instant instant) { return Timestamp.from(instant); }
    private static String hash(String value) {
        return StorySnapshotCanonicalizer.sha256(value.getBytes(StandardCharsets.UTF_8));
    }
    private static void count(Map<String, Long> counts, String key) { counts.merge(key, 1L, Long::sum); }

    record Story(UUID id, String anchor, String representative, String state, Instant from, Instant to,
                 long version, Instant createdAt, Instant updatedAt) { }
    record Membership(long id, UUID storyId, String ref, String fingerprint) { }
    record Input(long id, long articleId, String ref, String fingerprint, Instant effectiveAt,
                 Instant createdAt, String usability) { }
    record Basis(List<Story> stories, List<Membership> memberships, long commitId) { }
    record Plan(StorySnapshotRepository.Snapshot snapshot, Basis basis, List<Input> inputs,
                StoryPartitionService.Partition partition) { }
    record Result(String outcome, int changed, int skipped, Map<String, Long> counts) { }
}
