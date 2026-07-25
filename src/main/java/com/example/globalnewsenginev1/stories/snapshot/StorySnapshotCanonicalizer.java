package com.example.globalnewsenginev1.stories.snapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

final class StorySnapshotCanonicalizer {

    private StorySnapshotCanonicalizer() {
    }

    static Instant normalizeWatermark(Instant watermark) {
        return watermark.truncatedTo(ChronoUnit.MICROS);
    }

    static String snapshotInputHash(
            String versionKey,
            Instant watermark,
            List<StorySnapshotRepository.SnapshotInput> inputs
    ) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, "format", "story-snapshot-input-v1");
        field(canonical, "clusteringVersion", versionKey);
        field(canonical, "snapshotWatermark", normalizeWatermark(watermark).toString());
        for (StorySnapshotRepository.SnapshotInput input : inputs) {
            field(canonical, "articleRef", input.articleRef());
            field(canonical, "articleInputFingerprint", input.articleInputFingerprint());
            field(canonical, "embeddingArtifactId", Long.toString(input.embeddingArtifactId()));
            field(canonical, "vectorHash", input.vectorHash());
            field(canonical, "effectiveAt", input.effectiveAt().toString());
        }
        return sha256(canonical.toString());
    }

    static String snapshotKey(String versionKey, Instant watermark, String snapshotInputHash) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, "format", "story-snapshot-key-v1");
        field(canonical, "clusteringVersion", versionKey);
        field(canonical, "snapshotWatermark", normalizeWatermark(watermark).toString());
        field(canonical, "snapshotInputHash", snapshotInputHash);
        return sha256(canonical.toString());
    }

    static String runKey(
            String versionKey,
            String snapshotInputHash,
            StorySnapshotService.RunMode runMode,
            String pairRuleVersion
    ) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, "format", "story-pair-run-v1");
        field(canonical, "clusteringVersion", versionKey);
        field(canonical, "snapshotInputHash", snapshotInputHash);
        field(canonical, "runMode", runMode.name());
        field(canonical, "pairRuleVersion", pairRuleVersion);
        return sha256(canonical.toString());
    }

    static String decisionHash(
            StorySnapshotRepository.Snapshot snapshot,
            StorySnapshotRepository.SnapshotInput left,
            StorySnapshotRepository.SnapshotInput right,
            StorySnapshotService.PairDecision decision,
            String pairRuleVersion
    ) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, "format", "story-pair-decision-v1");
        field(canonical, "snapshotInputHash", snapshot.inputHash());
        appendInput(canonical, "left", left);
        appendInput(canonical, "right", right);
        field(canonical, "pairRuleVersion", pairRuleVersion);
        field(canonical, "result", decision.result());
        field(canonical, "cosineSimilarity", decision.similarity().toPlainString());
        field(canonical, "topOneBelowThreshold",
                Boolean.toString(decision.topOneBelowThreshold()));
        return sha256(canonical.toString());
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void appendInput(
            StringBuilder target,
            String side,
            StorySnapshotRepository.SnapshotInput input
    ) {
        field(target, side + "ArticleRef", input.articleRef());
        field(target, side + "ArticleInputFingerprint", input.articleInputFingerprint());
        field(target, side + "EmbeddingArtifactId", Long.toString(input.embeddingArtifactId()));
        field(target, side + "VectorHash", input.vectorHash());
    }

    private static void field(StringBuilder target, String name, String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        target.append(name).append('=').append(utf8.length).append(':').append(value).append('\n');
    }
}
