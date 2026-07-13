package com.example.globalnewsenginev1.articles.normalization;

public record GkgTone(
        Double value,
        Double positiveScore,
        Double negativeScore,
        Double polarity,
        Double activityReferenceDensity,
        Double selfGroupReferenceDensity,
        Integer wordCount
) {
}
