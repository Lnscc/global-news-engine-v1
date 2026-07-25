package com.example.globalnewsenginev1.stories.snapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;

final class ExactCosine {

    private ExactCosine() {
    }

    static float[] decode(byte[] vectorBytes, int dimension, String expectedHash) {
        if (vectorBytes == null || vectorBytes.length != dimension * Float.BYTES) {
            throw new InvalidVectorException("VECTOR_BYTE_LENGTH",
                    "Expected %d vector bytes but received %s"
                            .formatted(dimension * Float.BYTES,
                                    vectorBytes == null ? "null" : vectorBytes.length));
        }
        String actualHash = StorySnapshotCanonicalizer.sha256(vectorBytes);
        if (!actualHash.equals(expectedHash)) {
            throw new InvalidVectorException("VECTOR_HASH",
                    "Vector hash does not match the immutable artifact hash");
        }
        float[] values = new float[dimension];
        ByteBuffer buffer = ByteBuffer.wrap(vectorBytes);
        double squaredNorm = 0;
        for (int index = 0; index < dimension; index++) {
            float value = buffer.getFloat();
            if (!Float.isFinite(value)) {
                throw new InvalidVectorException("NON_FINITE_VECTOR",
                        "Vector contains NaN or Infinity");
            }
            values[index] = value;
            squaredNorm += (double) value * value;
        }
        double norm = Math.sqrt(squaredNorm);
        if (!Double.isFinite(norm) || norm == 0) {
            throw new InvalidVectorException("ZERO_NORM",
                    "Vector norm must be finite and positive");
        }
        return values;
    }

    static BigDecimal similarity(float[] left, float[] right) {
        return score(left, right).quantized();
    }

    static Score score(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new InvalidVectorException("DIMENSION_MISMATCH",
                    "Vector dimensions differ");
        }
        double dot = 0;
        double leftSquaredNorm = 0;
        double rightSquaredNorm = 0;
        for (int index = 0; index < left.length; index++) {
            double leftValue = left[index];
            double rightValue = right[index];
            dot += leftValue * rightValue;
            leftSquaredNorm += leftValue * leftValue;
            rightSquaredNorm += rightValue * rightValue;
        }
        double denominator = Math.sqrt(leftSquaredNorm) * Math.sqrt(rightSquaredNorm);
        if (!Double.isFinite(denominator) || denominator == 0) {
            throw new InvalidVectorException("ZERO_NORM",
                    "Vector norm must be finite and positive");
        }
        double cosine = Math.max(-1, Math.min(1, dot / denominator));
        return new Score(cosine,
                BigDecimal.valueOf(cosine).setScale(6, RoundingMode.HALF_UP));
    }

    record Score(double exact, BigDecimal quantized) {
    }

    static final class InvalidVectorException extends RuntimeException {
        private final String code;

        InvalidVectorException(String code, String message) {
            super(message);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
