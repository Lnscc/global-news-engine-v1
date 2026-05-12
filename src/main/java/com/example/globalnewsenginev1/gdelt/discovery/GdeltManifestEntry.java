package com.example.globalnewsenginev1.gdelt.discovery;

import com.example.globalnewsenginev1.gdelt.model.GdeltFileType;
public record GdeltManifestEntry(
        String batchTimestamp,
        GdeltFileType fileType,
        long sizeBytes,
        String fileHash,
        String url
) {
}
