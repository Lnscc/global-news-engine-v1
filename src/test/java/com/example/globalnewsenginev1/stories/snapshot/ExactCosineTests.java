package com.example.globalnewsenginev1.stories.snapshot;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExactCosineTests {

    @Test
    void decodesCanonicalBigEndianFloat32DimensionsAndComputesKnownScores() {
        byte[] bytes = ByteBuffer.allocate(3 * Float.BYTES)
                .putFloat(1.25f)
                .putFloat(-2.5f)
                .putFloat(0.75f)
                .array();

        float[] decoded = ExactCosine.decode(bytes, 3,
                StorySnapshotCanonicalizer.sha256(bytes));

        assertThat(decoded).containsExactly(1.25f, -2.5f, 0.75f);
        assertThat(ExactCosine.similarity(
                new float[]{1, 0}, new float[]{1, 0}))
                .isEqualByComparingTo("1.000000");
        assertThat(ExactCosine.similarity(
                new float[]{1, 0}, new float[]{0, 1}))
                .isEqualByComparingTo("0.000000");
        assertThat(ExactCosine.similarity(
                new float[]{1, 0}, new float[]{-1, 0}))
                .isEqualByComparingTo("-1.000000");
        assertThat(ExactCosine.similarity(
                new float[]{1, 0}, new float[]{0.7f, (float) Math.sqrt(0.51)}))
                .isEqualByComparingTo(new BigDecimal("0.700000"));
    }

    @Test
    void rejectsWrongLengthDimensionHashNonFiniteValuesAndZeroNorm() {
        byte[] valid = ByteBuffer.allocate(2 * Float.BYTES)
                .putFloat(1)
                .putFloat(0)
                .array();
        assertInvalid(new byte[3], 2,
                StorySnapshotCanonicalizer.sha256(new byte[3]), "VECTOR_BYTE_LENGTH");
        assertInvalid(valid, 1, StorySnapshotCanonicalizer.sha256(valid),
                "VECTOR_BYTE_LENGTH");
        assertInvalid(valid, 2, "0".repeat(64), "VECTOR_HASH");

        byte[] nan = ByteBuffer.allocate(2 * Float.BYTES)
                .putFloat(Float.NaN).putFloat(1).array();
        assertInvalid(nan, 2, StorySnapshotCanonicalizer.sha256(nan),
                "NON_FINITE_VECTOR");
        byte[] infinity = ByteBuffer.allocate(2 * Float.BYTES)
                .putFloat(Float.POSITIVE_INFINITY).putFloat(1).array();
        assertInvalid(infinity, 2, StorySnapshotCanonicalizer.sha256(infinity),
                "NON_FINITE_VECTOR");
        byte[] zero = new byte[2 * Float.BYTES];
        assertInvalid(zero, 2, StorySnapshotCanonicalizer.sha256(zero), "ZERO_NORM");
    }

    private void assertInvalid(byte[] bytes, int dimension, String hash, String code) {
        assertThatThrownBy(() -> ExactCosine.decode(bytes, dimension, hash))
                .isInstanceOfSatisfying(ExactCosine.InvalidVectorException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
