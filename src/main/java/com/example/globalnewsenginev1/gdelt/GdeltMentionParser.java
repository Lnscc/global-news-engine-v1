package com.example.globalnewsenginev1.gdelt;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Component
public class GdeltMentionParser {

    private static final int EXPECTED_COLUMNS = 16;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public Optional<GdeltMentionRecord> parse(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return Optional.empty();
        }

        String[] columns = rawLine.split("\\t", -1);
        if (columns.length < EXPECTED_COLUMNS) {
            return Optional.empty();
        }

        Long globalEventId = parseLong(columns[0]);
        LocalDateTime eventTimeDate = parseTimestamp(columns[1]);
        LocalDateTime mentionTimeDate = parseTimestamp(columns[2]);
        Integer mentionType = parseInteger(columns[3]);
        if (globalEventId == null || eventTimeDate == null || mentionTimeDate == null || mentionType == null) {
            return Optional.empty();
        }

        return Optional.of(new GdeltMentionRecord(
                globalEventId,
                eventTimeDate,
                mentionTimeDate,
                mentionType,
                blankToNull(columns[4]),
                blankToNull(columns[5]),
                parseInteger(columns[6]),
                parseInteger(columns[7]),
                parseInteger(columns[8]),
                parseInteger(columns[9]),
                "1".equals(columns[10]),
                parseInteger(columns[11]),
                parseInteger(columns[12]),
                parseDecimal(columns[13]),
                blankToNull(columns[14]),
                blankToNull(columns[15])
        ));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
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
}
