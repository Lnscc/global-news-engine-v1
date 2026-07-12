package com.example.globalnewsenginev1.gdelt.staging.parser;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageGkg;
import org.springframework.web.util.HtmlUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GdeltGkgParser {

    private static final int MINIMUM_COLUMNS = 16;
    private static final int EXTRAS_XML_COLUMN = 26;
    private static final int MAX_TITLE_LENGTH = 1_000;
    private static final Pattern PAGE_TITLE = Pattern.compile(
            "<PAGE_TITLE>(.*?)</PAGE_TITLE>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public GdeltStageGkg parse(String rawTsv) {
        String[] columns = GdeltTsv.split(rawTsv, MINIMUM_COLUMNS, "GKG");
        return new GdeltStageGkg(
                GdeltTsv.requiredText(columns, 0, "gkg_record_id"),
                GdeltTsv.timestamp(columns, 1, "document_date"),
                GdeltTsv.integer(columns, 2, "source_collection_identifier"),
                GdeltTsv.text(columns, 3),
                GdeltTsv.text(columns, 4),
                GdeltTsv.text(columns, 7),
                GdeltTsv.text(columns, 11),
                GdeltTsv.text(columns, 13),
                GdeltTsv.text(columns, 9),
                GdeltTsv.text(columns, 15),
                pageTitle(columns)
        );
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
}
