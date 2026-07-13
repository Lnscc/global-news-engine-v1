package com.example.globalnewsenginev1.articles.normalization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class GkgValueNormalizer {

    public NormalizedGkgValues normalize(
            String personsRaw,
            String organizationsRaw,
            String locationsRaw,
            String toneRaw
    ) {
        LocationResult locationResult = normalizeLocations(locationsRaw);
        return new NormalizedGkgValues(
                normalizeNames(personsRaw),
                normalizeNames(organizationsRaw),
                locationResult.locations(),
                normalizeTone(toneRaw),
                locationResult.discardedCount());
    }

    public List<String> normalizeNames(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : raw.split(";", -1)) {
            String value = item.trim();
            if (!value.isEmpty()) values.add(value);
        }
        return List.copyOf(values);
    }

    public LocationResult normalizeLocations(String raw) {
        if (raw == null || raw.isBlank()) return new LocationResult(List.of(), 0);
        List<GkgLocation> locations = new ArrayList<>();
        int discarded = 0;
        for (String item : raw.split(";", -1)) {
            if (item.isBlank()) continue;
            String[] fields = item.split("#", -1);
            if (fields.length != 7) {
                discarded++;
                continue;
            }
            Integer type = integer(fields[0]);
            ParsedDouble latitude = nullableDouble(fields[4]);
            ParsedDouble longitude = nullableDouble(fields[5]);
            if (type == null || !latitude.valid() || !longitude.valid()) {
                discarded++;
                continue;
            }
            locations.add(new GkgLocation(
                    type,
                    nullableText(fields[1]),
                    nullableText(fields[2]),
                    nullableText(fields[3]),
                    latitude.value(),
                    longitude.value(),
                    nullableText(fields[6])));
        }
        return new LocationResult(List.copyOf(locations), discarded);
    }

    public GkgTone normalizeTone(String raw) {
        String[] fields = raw == null ? new String[0] : raw.split(",", -1);
        return new GkgTone(
                toneDouble(fields, 0),
                toneDouble(fields, 1),
                toneDouble(fields, 2),
                toneDouble(fields, 3),
                toneDouble(fields, 4),
                toneDouble(fields, 5),
                toneInteger(fields, 6));
    }

    private Double toneDouble(String[] fields, int index) {
        if (index >= fields.length || fields[index].isBlank()) return null;
        try {
            double value = Double.parseDouble(fields[index].trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer toneInteger(String[] fields, int index) {
        if (index >= fields.length || fields[index].isBlank()) return null;
        return integer(fields[index]);
    }

    private Integer integer(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ParsedDouble nullableDouble(String raw) {
        if (raw.isBlank()) return new ParsedDouble(null, true);
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value)
                    ? new ParsedDouble(value, true)
                    : new ParsedDouble(null, false);
        } catch (NumberFormatException exception) {
            return new ParsedDouble(null, false);
        }
    }

    private String nullableText(String raw) {
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    public record LocationResult(List<GkgLocation> locations, int discardedCount) {
    }

    private record ParsedDouble(Double value, boolean valid) {
    }
}
