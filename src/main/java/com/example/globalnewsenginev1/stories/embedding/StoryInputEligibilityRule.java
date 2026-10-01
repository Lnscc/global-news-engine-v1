package com.example.globalnewsenginev1.stories.embedding;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

final class StoryInputEligibilityRule {

    static final String INCLUDE_ALL_VERSION = "all-articles-v1";
    static final String NAVIGATION_SERVICE_VERSION = "navigation-service-exact-path-title-v1";
    static final String NAVIGATION_SERVICE_REASON = "NAVIGATION_SERVICE";

    private static final Map<String, String> NAVIGATION_TITLES = Map.of(
            "/about", "about",
            "/advertise", "advertise",
            "/meet-the-team", "meet the team",
            "/privacy", "privacy policy",
            "/resources", "resources",
            "/support", "support");

    private StoryInputEligibilityRule() {
    }

    static Decision evaluate(String ruleVersion, String canonicalUrl, String normalizedTitle) {
        if (INCLUDE_ALL_VERSION.equals(ruleVersion)) {
            return Decision.INCLUDE;
        }
        if (!NAVIGATION_SERVICE_VERSION.equals(ruleVersion)) {
            throw new IllegalArgumentException("Unsupported story input eligibility rule: " + ruleVersion);
        }
        if (normalizedTitle == null) {
            return Decision.INCLUDE;
        }
        try {
            String expectedTitle = NAVIGATION_TITLES.get(URI.create(canonicalUrl).getPath());
            String title = normalizedTitle.toLowerCase(Locale.ROOT);
            if (expectedTitle != null
                    && (title.equals(expectedTitle) || title.startsWith(expectedTitle + " "))) {
                return new Decision("EXCLUDE", NAVIGATION_SERVICE_REASON);
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid canonical URLs stay included conservatively.
        }
        return Decision.INCLUDE;
    }

    record Decision(String disposition, String reason) {
        private static final Decision INCLUDE = new Decision("INCLUDE", null);

        boolean excluded() {
            return "EXCLUDE".equals(disposition);
        }
    }
}
