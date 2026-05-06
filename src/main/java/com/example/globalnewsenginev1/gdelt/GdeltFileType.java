package com.example.globalnewsenginev1.gdelt;

import java.util.Arrays;
import java.util.Optional;

public enum GdeltFileType {
    EVENTS(".export.CSV.zip"),
    MENTIONS(".mentions.CSV.zip"),
    GKG(".gkg.csv.zip");

    private final String suffix;

    GdeltFileType(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }

    public static Optional<GdeltFileType> fromFileName(String fileName, String batchTimestamp) {
        return Arrays.stream(values())
                .filter(fileType -> fileName.equals(batchTimestamp + fileType.suffix))
                .findFirst();
    }
}
