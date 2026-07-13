package com.example.globalnewsenginev1.articles.normalization;

import java.util.List;

public record NormalizedGkgValues(
        List<String> persons,
        List<String> organizations,
        List<GkgLocation> locations,
        GkgTone tone,
        int discardedLocationCount
) {
}
