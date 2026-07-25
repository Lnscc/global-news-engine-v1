package com.example.globalnewsenginev1.stories.embedding;

import java.nio.ByteBuffer;

final class EmbeddingVectorCodec {

    private EmbeddingVectorCodec() {
    }

    static EncodedVector encode(float[] input, int expectedDimension) {
        if (input.length != expectedDimension) {
            throw new InvalidVectorException("DIMENSION_MISMATCH",
                    "Expected %d dimensions but received %d".formatted(expectedDimension, input.length));
        }
        double squaredNorm = 0;
        for (float value : input) {
            if (!Float.isFinite(value)) {
                throw new InvalidVectorException("NON_FINITE_VECTOR", "Vector contains NaN or Infinity");
            }
            squaredNorm += (double) value * value;
        }
        double norm = Math.sqrt(squaredNorm);
        if (!Double.isFinite(norm) || norm == 0) {
            throw new InvalidVectorException("ZERO_NORM", "Vector norm must be finite and positive");
        }
        ByteBuffer bytes = ByteBuffer.allocate(expectedDimension * Float.BYTES);
        for (float value : input) {
            bytes.putFloat((float) (value / norm));
        }
        byte[] canonical = bytes.array();
        return new EncodedVector(canonical, StoryTitleNormalizer.sha256(canonical), norm);
    }

    record EncodedVector(byte[] bytes, String hash, double originalNorm) {
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
