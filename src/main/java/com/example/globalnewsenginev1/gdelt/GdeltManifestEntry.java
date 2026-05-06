package com.example.globalnewsenginev1.gdelt;

public record GdeltManifestEntry(
        String batchTimestamp,
        GdeltFileType fileType,
        long sizeBytes,
        String fileHash,
        String url
) {
}
