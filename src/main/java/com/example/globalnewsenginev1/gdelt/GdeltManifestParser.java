package com.example.globalnewsenginev1.gdelt;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class GdeltManifestParser {

    private static final int EXPECTED_PARTS = 3;

    public List<GdeltManifestEntry> parse(String manifestBody) {
        List<GdeltManifestEntry> entries = new ArrayList<>();

        for (String line : manifestBody.lines().toList()) {
            parseLine(line).ifPresent(entries::add);
        }

        return entries;
    }

    private Optional<GdeltManifestEntry> parseLine(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        String[] parts = line.trim().split("\\s+");
        if (parts.length != EXPECTED_PARTS) {
            return Optional.empty();
        }

        String url = parts[2];
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        int firstDot = fileName.indexOf('.');
        if (firstDot < 0) {
            return Optional.empty();
        }

        String batchTimestamp = fileName.substring(0, firstDot);
        if (!isGdeltV2Timestamp(batchTimestamp)) {
            return Optional.empty();
        }

        Optional<GdeltFileType> fileType = GdeltFileType.fromFileName(fileName, batchTimestamp);
        if (fileType.isEmpty()) {
            return Optional.empty();
        }

        long sizeBytes;
        try {
            sizeBytes = Long.parseLong(parts[0]);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }

        return Optional.of(new GdeltManifestEntry(
                batchTimestamp,
                fileType.get(),
                sizeBytes,
                parts[1],
                url
        ));
    }

    private boolean isGdeltV2Timestamp(String value) {
        return value.length() == 14 && value.chars().allMatch(Character::isDigit);
    }
}
