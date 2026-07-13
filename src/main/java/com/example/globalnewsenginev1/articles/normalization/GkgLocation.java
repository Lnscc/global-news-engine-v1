package com.example.globalnewsenginev1.articles.normalization;

public record GkgLocation(
        Integer type,
        String name,
        String countryCode,
        String adm1Code,
        Double latitude,
        Double longitude,
        String featureId
) {
}
