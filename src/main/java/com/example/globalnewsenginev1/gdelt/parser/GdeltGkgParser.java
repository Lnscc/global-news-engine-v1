package com.example.globalnewsenginev1.gdelt.parser;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GdeltGkgParser {

    private static final int EXPECTED_COLUMNS = 27;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public Optional<GdeltGkgRecord> parse(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return Optional.empty();
        }

        String[] columns = rawLine.split("\\t", -1);
        if (columns.length < EXPECTED_COLUMNS) {
            return Optional.empty();
        }

        String gkgRecordId = blankToNull(columns[0]);
        LocalDateTime date = parseTimestamp(columns[1]);
        if (gkgRecordId == null || date == null) {
            return Optional.empty();
        }

        BigDecimal[] toneParts = parseToneParts(columns[15]);
        return Optional.of(new GdeltGkgRecord(
                gkgRecordId,
                date,
                parseInteger(columns[2]),
                blankToNull(columns[3]),
                blankToNull(columns[4]),
                blankToNull(columns[5]),
                blankToNull(columns[6]),
                blankToNull(columns[7]),
                blankToNull(columns[8]),
                blankToNull(columns[9]),
                blankToNull(columns[10]),
                blankToNull(columns[11]),
                blankToNull(columns[12]),
                blankToNull(columns[13]),
                blankToNull(columns[14]),
                toneParts[0],
                toneParts[1],
                toneParts[2],
                toneParts[3],
                toneParts[4],
                toneParts[5],
                toneParts[6],
                blankToNull(columns[16]),
                blankToNull(columns[17]),
                blankToNull(columns[18]),
                blankToNull(columns[19]),
                blankToNull(columns[20]),
                blankToNull(columns[21]),
                blankToNull(columns[22]),
                blankToNull(columns[23]),
                blankToNull(columns[24]),
                blankToNull(columns[25]),
                blankToNull(columns[26])
        ));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Integer parseInteger(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, TIMESTAMP_FORMAT);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private BigDecimal[] parseToneParts(String value) {
        BigDecimal[] parts = new BigDecimal[7];
        if (value == null || value.isBlank()) {
            return parts;
        }

        String[] rawParts = value.split(",", -1);
        for (int i = 0; i < parts.length && i < rawParts.length; i++) {
            parts[i] = parseDecimal(rawParts[i]);
        }
        return parts;
    }
}
