package com.example.globalnewsenginev1.gdelt.staging.parser;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

final class GdeltTsv {

    private static final DateTimeFormatter GDELT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter GDELT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private GdeltTsv() {
    }

    static String[] split(String rawTsv, int minimumColumns, String dataset) {
        String[] columns = rawTsv.split("\t", -1);
        if (columns.length < minimumColumns) {
            throw new GdeltParseException("COLUMN_COUNT",
                    dataset + " row has " + columns.length + " columns, expected at least " + minimumColumns);
        }
        return columns;
    }

    static String text(String[] columns, int index) {
        String value = columns[index];
        return value.isBlank() ? null : value;
    }

    static String requiredText(String[] columns, int index, String field) {
        String value = text(columns, index);
        if (value == null) {
            throw new GdeltParseException("REQUIRED_FIELD", field + " is required");
        }
        return value;
    }

    static Long requiredLong(String[] columns, int index, String field) {
        String value = requiredText(columns, index, field);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new GdeltParseException("INVALID_NUMBER", field + " is not a valid long: " + value);
        }
    }

    static Integer integer(String[] columns, int index, String field) {
        String value = text(columns, index);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new GdeltParseException("INVALID_NUMBER", field + " is not a valid integer: " + value);
        }
    }

    static Double decimal(String[] columns, int index, String field) {
        String value = text(columns, index);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new GdeltParseException("INVALID_NUMBER", field + " is not a valid decimal: " + value);
        }
    }

    static LocalDate date(String[] columns, int index, String field) {
        String value = text(columns, index);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, GDELT_DATE);
        } catch (DateTimeParseException exception) {
            throw new GdeltParseException("INVALID_DATE", field + " is not a valid yyyyMMdd date: " + value);
        }
    }

    static Instant timestamp(String[] columns, int index, String field) {
        String value = text(columns, index);
        if (value == null) {
            return null;
        }
        try {
            return Instant.from(GDELT_TIMESTAMP.parse(value));
        } catch (DateTimeParseException exception) {
            throw new GdeltParseException("INVALID_TIMESTAMP",
                    field + " is not a valid yyyyMMddHHmmss timestamp: " + value);
        }
    }
}
