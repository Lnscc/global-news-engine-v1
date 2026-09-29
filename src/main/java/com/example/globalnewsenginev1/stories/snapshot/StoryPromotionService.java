package com.example.globalnewsenginev1.stories.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Internal operations service; deliberately exposes no REST endpoint. */
@Service
public class StoryPromotionService {
    private final JdbcTemplate jdbc;
    private final StorySnapshotRepository repository;
    private final StoryPublisher publisher;
    private final TransactionTemplate transaction;

    StoryPromotionService(JdbcTemplate jdbc, StorySnapshotRepository repository, StoryPublisher publisher,
                          PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.publisher = publisher;
        transaction = new TransactionTemplate(manager);
    }

    public record Diff(int previousStories, int candidateStories, double previousSingletonShare,
                       double candidateSingletonShare, int previousMemberships, int candidateMemberships,
                       int changedMemberships, int merges, int splits) { }
    public record Review(long versionId, Long previousVersionId, String basisHash,
                         Diff diff, StoryReleaseEvaluation.Result evaluation) { }
    public record Approval(String approvedBy, String document, String reason) { }
    public enum Outcome { PROMOTED, ALREADY_PROMOTED }

    public Review review(long versionId, StoryReleaseEvaluation.Holdout holdout) {
        return transaction.execute(status -> {
            Long previous = lock(versionId);
            return prepare(versionId, previous, holdout).review();
        });
    }

    /** The reviewed basis is checked again under the same locks used by normal publishers. */
    public Outcome promote(Review reviewed, StoryReleaseEvaluation.Holdout holdout, Approval approval) {
        Objects.requireNonNull(reviewed, "Review is required");
        return transaction.execute(status -> {
            Long previous = lock(reviewed.versionId());
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM story_clustering_version_status_history
                    WHERE clustering_version_id = ? AND previous_status = 'SHADOW' AND new_status = 'ACTIVE')
                    """, Boolean.class, reviewed.versionId()))) return Outcome.ALREADY_PROMOTED;
            Objects.requireNonNull(approval, "Documented approval is required");
            StoryReleaseEvaluation.requireText(approval.approvedBy(), "Approver");
            StoryReleaseEvaluation.requireText(approval.document(), "Approval document");
            StoryReleaseEvaluation.requireText(approval.reason(), "Approval reason");
            Prepared prepared = prepare(reviewed.versionId(), previous, holdout);
            if (!prepared.review().equals(reviewed)) {
                throw new IllegalStateException("Published basis or evidence changed; obtain a new review and approval");
            }
            if (!reviewed.evaluation().passed()) throw new IllegalStateException("Release evaluation gates failed");
            Instant now = Instant.now();
            for (var identity : prepared.identities()) {
                jdbc.update("""
                        UPDATE stories SET public_id = ?, identity_anchor_article_ref = ?, created_at = ?,
                            optimistic_version = optimistic_version + 1, updated_at = ?
                        WHERE id = ? AND clustering_version_id = ?
                        """, identity.publicId(), identity.anchor(), Timestamp.from(identity.createdAt()),
                        Timestamp.from(now), identity.internalId(), reviewed.versionId());
            }
            String audit = audit(reviewed, holdout, approval);
            if (previous != null) transition(previous, "ACTIVE", "RETIRED", audit, now);
            transition(reviewed.versionId(), "SHADOW", "ACTIVE", audit, now);
            return Outcome.PROMOTED;
        });
    }

    /** Resolve public identity in one statement so readers never combine two version states. */
    public Optional<UUID> resolvePublicId(UUID publicId) {
        return jdbc.query("""
                SELECT story.id FROM stories story JOIN story_clustering_versions version
                  ON version.id = story.clustering_version_id
                WHERE version.status = 'ACTIVE' AND story.public_id = ? AND story.state <> 'SUPERSEDED'
                """, (rs, row) -> rs.getObject(1, UUID.class), publicId).stream().findFirst();
    }

    private Long lock(long target) {
        // ponytail: serialize rare promotions globally; normal publication still locks per version.
        jdbc.query("SELECT pg_advisory_xact_lock(-390039)", rs -> null);
        List<Long> active = jdbc.queryForList(
                "SELECT id FROM story_clustering_versions WHERE status = 'ACTIVE'", Long.class);
        Long previous = active.isEmpty() ? null : active.getFirst();
        var ids = new java.util.TreeSet<Long>();
        ids.add(target);
        if (previous != null) ids.add(previous);
        for (long id : ids) {
            jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> null, id);
            jdbc.queryForObject("SELECT id FROM story_clustering_versions WHERE id = ? FOR UPDATE", Long.class, id);
        }
        // Freeze the completeness check against embedding writers until this transaction ends.
        jdbc.execute("LOCK TABLE story_article_inputs IN SHARE MODE");
        return previous;
    }

    private Prepared prepare(long target, Long previous, StoryReleaseEvaluation.Holdout holdout) {
        if (!"SHADOW".equals(jdbc.queryForObject(
                "SELECT status FROM story_clustering_versions WHERE id = ?", String.class, target))) {
            throw new IllegalStateException("Only a SHADOW version can be reviewed");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM story_processing_runs
                WHERE clustering_version_id = ? AND status IN ('PENDING', 'RUNNING'))
                """, Boolean.class, target))) throw new IllegalStateException("Candidate processing is unfinished");
        long commit = publisher.latestCommit(target);
        if (commit == 0) throw new IllegalStateException("Candidate has no published snapshot");
        var snapshot = jdbc.queryForObject("""
                SELECT snapshot.* FROM story_publish_commits commit
                JOIN story_snapshots snapshot ON snapshot.id = commit.snapshot_id
                JOIN story_processing_runs run ON run.id = commit.run_id
                WHERE commit.id = ? AND run.status = 'SUCCEEDED' AND run.failed_article_count = 0
                  AND run.read_article_count = (SELECT COUNT(*) FROM story_snapshot_members WHERE snapshot_id = snapshot.id)
                """, (rs, row) -> new StorySnapshotRepository.Snapshot(rs.getLong("id"), target,
                rs.getString("snapshot_key"), rs.getTimestamp("snapshot_watermark").toInstant(),
                rs.getString("snapshot_input_hash"), false), commit);
        var rules = repository.loadPartitionRules(snapshot.id());
        var inputs = repository.findSnapshotInputs(target, snapshot.watermark());
        if (inputs.isEmpty() || !snapshot.inputHash().equals(StorySnapshotCanonicalizer.snapshotInputHash(
                rules.versionKey(), snapshot.watermark(), inputs))) {
            throw new IllegalStateException("Candidate snapshot is empty or inputs have changed; publish a fresh snapshot");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM articles article WHERE article.first_seen_at <= ?
                  AND NOT EXISTS (SELECT 1 FROM story_article_inputs input
                    WHERE input.clustering_version_id = ? AND input.article_id = article.id AND input.current_marker = 1))
                """, Boolean.class, Timestamp.from(snapshot.watermark()), target))) {
            throw new IllegalStateException("Candidate lacks article inputs");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM story_snapshot_members member
                  JOIN story_article_inputs input ON input.id = member.article_input_id
                  LEFT JOIN story_embedding_artifacts artifact ON artifact.id = member.embedding_artifact_id
                  JOIN story_clustering_versions version ON version.id = member.clustering_version_id
                  WHERE member.snapshot_id = ? AND input.title_usability = 'USABLE'
                    AND input.effective_at <= ? AND (input.embedding_status <> 'READY'
                      OR artifact.id IS NULL OR artifact.status <> 'READY'
                      OR artifact.embedding_dimension <> version.embedding_dimension
                      OR artifact.embedding_model_id <> version.embedding_model_id
                      OR artifact.embedding_model_version <> version.embedding_model_version
                      OR artifact.title_normalization_version <> version.title_normalization_version
                      OR artifact.title_input_hash <> input.title_input_hash))
                """, Boolean.class, snapshot.id(), Timestamp.from(snapshot.watermark())))) {
            throw new IllegalStateException("Candidate embeddings are incomplete or incompatible");
        }
        var frozen = repository.loadSnapshotInputs(snapshot.id());
        Map<String, String> fingerprints = new HashMap<>();
        frozen.forEach(input -> fingerprints.put(input.articleRef(), input.articleInputFingerprint()));
        var partition = StoryPartitionService.partition(rules, frozen, false);
        List<StoryPublisher.Story> current = publisher.stories(target);
        List<StoryPublisher.Membership> currentMembers = publisher.memberships(target);
        if (currentMembers.stream().anyMatch(member -> !member.fingerprint().equals(fingerprints.get(member.ref())))) {
            throw new IllegalStateException("Published memberships differ from snapshot inputs");
        }
        Map<UUID, StoryPublisher.Story> byId = new HashMap<>();
        current.forEach(story -> byId.put(story.id(), story));
        Map<String, UUID> assigned = new HashMap<>();
        currentMembers.forEach(member -> assigned.put(member.ref(), member.storyId()));
        Map<String, Integer> componentByArticle = new HashMap<>();
        List<StoryPublisher.Story> candidates = new ArrayList<>();
        for (int i = 0; i < partition.components().size(); i++) {
            var component = partition.components().get(i);
            UUID internalId = assigned.get(component.members().getFirst().articleRef());
            StoryPublisher.Story story = byId.get(internalId);
            if (story == null) throw new IllegalStateException("Published candidate differs from its snapshot");
            candidates.add(story);
            for (var member : component.members()) {
                componentByArticle.put(member.articleRef(), i);
                if (!internalId.equals(assigned.get(member.articleRef()))) {
                    throw new IllegalStateException("Published candidate differs from its snapshot");
                }
            }
        }
        if (componentByArticle.size() != assigned.size() || candidates.size() != current.size()
                || candidates.stream().map(StoryPublisher.Story::id).distinct().count() != candidates.size()) {
            throw new IllegalStateException("Published candidate membership coverage is incomplete");
        }
        List<StoryPublisher.Story> old = previous == null ? List.of() : publisher.stories(previous);
        List<StoryPublisher.Membership> oldMembers = previous == null ? List.of() : publisher.memberships(previous);
        if (previous != null && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM story_publish_commits commit
                  JOIN story_snapshots snapshot ON snapshot.id = commit.snapshot_id
                  WHERE commit.clustering_version_id = ? AND snapshot.snapshot_watermark > ?)
                """, Boolean.class, previous, Timestamp.from(snapshot.watermark())))) {
            throw new IllegalStateException("Candidate is older than the active version");
        }
        var inputRefs = inputs.stream().map(StorySnapshotRepository.SnapshotInput::articleRef).collect(java.util.stream.Collectors.toSet());
        if (oldMembers.stream().anyMatch(member -> !inputRefs.contains(member.ref()))) {
            throw new IllegalStateException("Candidate omits current active members");
        }
        var match = StoryIdentity.match(old, oldMembers, componentByArticle);
        List<Identity> identities = new ArrayList<>();
        Map<String, UUID> publicAssignments = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            var winner = StoryIdentity.winner(match.nominees().getOrDefault(i, List.of()));
            var identity = new Identity(candidate.id(), winner == null ? candidate.publicId() : winner.publicId(),
                    winner == null ? candidate.anchor() : winner.anchor(),
                    winner == null ? candidate.createdAt() : winner.createdAt());
            identities.add(identity);
            for (var member : partition.components().get(i).members()) publicAssignments.put(member.articleRef(), identity.publicId());
        }
        Map<UUID, UUID> oldPublicIds = new HashMap<>();
        old.forEach(story -> oldPublicIds.put(story.id(), story.publicId()));
        Map<String, UUID> before = new HashMap<>();
        oldMembers.forEach(member -> before.put(member.ref(), oldPublicIds.get(member.storyId())));
        var allRefs = new java.util.HashSet<>(before.keySet());
        allRefs.addAll(publicAssignments.keySet());
        int changed = (int) allRefs.stream().filter(ref -> !Objects.equals(before.get(ref), publicAssignments.get(ref))).count();
        Diff diff = new Diff(old.size(), current.size(), singletonShare(oldMembers), singletonShare(currentMembers),
                oldMembers.size(), currentMembers.size(), changed,
                (int) match.nominees().values().stream().filter(list -> list.size() > 1).count(), match.splits());
        Map<String, String> titleHashes = new HashMap<>();
        frozen.forEach(input -> titleHashes.put(input.articleRef(), input.titleInputHash()));
        var evaluation = StoryReleaseEvaluation.evaluate(holdout, publicAssignments, titleHashes);
        String basis = StoryReleaseEvaluation.hash(target + ":" + previous + ":" + commit + ":"
                + (previous == null ? 0 : publisher.latestCommit(previous)) + ":" + snapshot.inputHash()
                + ":" + old + ":" + oldMembers + ":" + current + ":" + currentMembers);
        return new Prepared(new Review(target, previous, basis, diff, evaluation), identities);
    }

    private double singletonShare(List<StoryPublisher.Membership> members) {
        Map<UUID, Integer> counts = new HashMap<>();
        members.forEach(member -> counts.merge(member.storyId(), 1, Integer::sum));
        return counts.isEmpty() ? 0 : (double) counts.values().stream().filter(count -> count == 1).count() / counts.size();
    }

    private void transition(long version, String from, String to, String audit, Instant now) {
        int changed = jdbc.update("UPDATE story_clustering_versions SET status = ?, updated_at = ? WHERE id = ? AND status = ?",
                to, Timestamp.from(now), version, from);
        if (changed != 1) throw new IllegalStateException("Clustering status changed during promotion");
        jdbc.update("""
                INSERT INTO story_clustering_version_status_history
                  (clustering_version_id, previous_status, new_status, reason, changed_at) VALUES (?, ?, ?, ?, ?)
                """, version, from, to, audit, Timestamp.from(now));
    }

    private String audit(Review review, StoryReleaseEvaluation.Holdout holdout, Approval approval) {
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("review", review);
            record.put("holdout", holdout);
            record.put("approval", approval);
            return new ObjectMapper().writeValueAsString(record);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Cannot serialize release evidence", exception);
        }
    }

    private record Identity(UUID internalId, UUID publicId, String anchor, Instant createdAt) { }
    private record Prepared(Review review, List<Identity> identities) { }
}
