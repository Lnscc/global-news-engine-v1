package com.example.globalnewsenginev1.gdelt;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Component
public class GdeltEventParser {

    private static final int EXPECTED_COLUMNS = 61;
    private static final DateTimeFormatter DATE_ADDED_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public Optional<GdeltEventRecord> parse(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return Optional.empty();
        }

        String[] columns = rawLine.split("\\t", -1);
        if (columns.length < EXPECTED_COLUMNS) {
            return Optional.empty();
        }

        Long globalEventId = parseLong(columns[0]);
        LocalDate eventDate = parseDate(columns[1]);
        Integer eventYear = parseInteger(columns[3]);
        if (globalEventId == null || eventDate == null || eventYear == null) {
            return Optional.empty();
        }

        return Optional.of(new GdeltEventRecord(
                globalEventId,
                eventDate,
                eventYear,
                blankToNull(columns[5]),
                blankToNull(columns[6]),
                blankToNull(columns[7]),
                blankToNull(columns[15]),
                blankToNull(columns[16]),
                blankToNull(columns[17]),
                "1".equals(columns[25]),
                blankToNull(columns[26]),
                blankToNull(columns[27]),
                blankToNull(columns[28]),
                parseInteger(columns[29]),
                parseDecimal(columns[30]),
                parseInteger(columns[31]),
                parseInteger(columns[32]),
                parseInteger(columns[33]),
                parseDecimal(columns[34]),
                blankToNull(columns[52]),
                blankToNull(columns[53]),
                parseDecimal(columns[56]),
                parseDecimal(columns[57]),
                parseDateAdded(columns[59]),
                blankToNull(columns[60])
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

    private LocalDate parseDate(String value) {
        if (value == null || value.length() != 8) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(value.substring(0, 4)),
                    Integer.parseInt(value.substring(4, 6)),
                    Integer.parseInt(value.substring(6, 8))
            );
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private LocalDateTime parseDateAdded(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_ADDED_FORMAT);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
