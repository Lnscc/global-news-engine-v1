package com.example.globalnewsenginev1.stories.embedding;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class StoryTitleNormalizer {

    public static final String NORMALIZATION_VERSION = "art031-title-nfkc-ws-v1";
    public static final String GENERIC_TITLE_RULE_VERSION = "art031-generic-title-v1";

    private static final Pattern UNICODE_WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");
    private static final Set<String> GENERIC_TITLES = Set.of(
            "deadline", "health", "ckia", "npr news", "targeted news service");

    public TitleInput normalize(String title) {
        if (title == null) {
            return new TitleInput(null, null, TitleInput.TitleUsability.TITLE_MISSING);
        }
        String normalized = UNICODE_WHITESPACE.matcher(Normalizer.normalize(
                HtmlUtils.htmlUnescape(title), Normalizer.Form.NFKC)).replaceAll(" ").trim();
        if (normalized.isEmpty()) {
            return new TitleInput(null, null, TitleInput.TitleUsability.TITLE_MISSING);
        }
        if (GENERIC_TITLES.contains(caseFold(normalized))) {
            return new TitleInput(normalized, null, TitleInput.TitleUsability.TITLE_GENERIC);
        }
        return new TitleInput(normalized, sha256(normalized.getBytes(StandardCharsets.UTF_8)),
                TitleInput.TitleUsability.USABLE);
    }

    private String caseFold(String value) {
        return value.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM", exception);
        }
    }
}
