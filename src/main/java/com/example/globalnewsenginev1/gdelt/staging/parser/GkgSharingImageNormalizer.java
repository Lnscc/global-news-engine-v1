package com.example.globalnewsenginev1.gdelt.staging.parser;

import java.net.URI;
import java.net.URISyntaxException;

public final class GkgSharingImageNormalizer {

    public static final int MAX_URL_LENGTH = 2_048;

    private GkgSharingImageNormalizer() {
    }

    public static String normalize(String rawValue) {
        if (rawValue == null) return null;
        String value = rawValue.strip();
        if (value.isEmpty() || value.length() > MAX_URL_LENGTH) return null;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!uri.isAbsolute()
                    || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getRawAuthority() == null
                    || uri.getRawAuthority().isBlank()) {
                return null;
            }
            return value;
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
