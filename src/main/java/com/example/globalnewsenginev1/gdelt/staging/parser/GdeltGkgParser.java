package com.example.globalnewsenginev1.gdelt.staging.parser;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageGkg;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GdeltGkgParser {

    private static final int MINIMUM_COLUMNS = 16;
    private static final int EXTRAS_XML_COLUMN = 26;
    private static final int SHARING_IMAGE_COLUMN = 18;
    private static final int MAX_TITLE_LENGTH = 1_000;
    private static final Duration MAX_PUBLICATION_TIME_FUTURE_SKEW = Duration.ofMinutes(15);
    private static final DateTimeFormatter PRECISE_PUBLICATION_TIME =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern PAGE_TITLE = Pattern.compile(
            "<PAGE_TITLE>(.*?)</PAGE_TITLE>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PAGE_PRECISE_PUB_TIMESTAMP = Pattern.compile(
            "<PAGE_PRECISEPUBTIMESTAMP>(.*?)</PAGE_PRECISEPUBTIMESTAMP>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public GdeltStageGkg parse(String rawTsv) {
        String[] columns = GdeltTsv.split(rawTsv, MINIMUM_COLUMNS, "GKG");
        Instant documentDate = GdeltTsv.timestamp(columns, 1, "document_date");
        return new GdeltStageGkg(
                GdeltTsv.requiredText(columns, 0, "gkg_record_id"),
                documentDate,
                GdeltTsv.integer(columns, 2, "source_collection_identifier"),
                GdeltTsv.text(columns, 3),
                GdeltTsv.text(columns, 4),
                GdeltTsv.text(columns, 7),
                GdeltTsv.text(columns, 11),
                GdeltTsv.text(columns, 13),
                GdeltTsv.text(columns, 9),
                GdeltTsv.text(columns, 15),
                sharingImageUrl(columns),
                pageTitle(columns),
                pagePrecisePublicationTime(columns, documentDate)
        );
    }

    private String sharingImageUrl(String[] columns) {
        if (columns.length <= SHARING_IMAGE_COLUMN) return null;
        return GkgSharingImageNormalizer.normalize(columns[SHARING_IMAGE_COLUMN]);
    }

    private String pageTitle(String[] columns) {
        if (columns.length <= EXTRAS_XML_COLUMN) {
            return null;
        }
        Matcher matcher = PAGE_TITLE.matcher(columns[EXTRAS_XML_COLUMN]);
        while (matcher.find()) {
            String title = HtmlUtils.htmlUnescape(matcher.group(1)).strip();
            if (!title.isEmpty()) {
                return title.substring(0, Math.min(title.length(), MAX_TITLE_LENGTH));
            }
        }
        return null;
    }

    private Instant pagePrecisePublicationTime(String[] columns, Instant documentDate) {
        if (columns.length <= EXTRAS_XML_COLUMN) {
            return null;
        }
        Matcher matcher = PAGE_PRECISE_PUB_TIMESTAMP.matcher(columns[EXTRAS_XML_COLUMN]);
        while (matcher.find()) {
            String value = matcher.group(1).strip();
            try {
                Instant publicationTime = LocalDateTime.parse(value, PRECISE_PUBLICATION_TIME)
                        .toInstant(ZoneOffset.UTC);
                if (documentDate == null
                        || !publicationTime.isAfter(documentDate.plus(MAX_PUBLICATION_TIME_FUTURE_SKEW))) {
                    return publicationTime;
                }
            } catch (DateTimeParseException ignored) {
                // Invalid optional metadata must not reject an otherwise valid GKG row.
            }
        }
        return null;
    }
}
