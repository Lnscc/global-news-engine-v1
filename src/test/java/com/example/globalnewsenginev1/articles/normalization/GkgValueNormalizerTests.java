package com.example.globalnewsenginev1.articles.normalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GkgValueNormalizerTests {

    private final GkgValueNormalizer normalizer = new GkgValueNormalizer();

    @Test
    void normalizesOrderedUniqueNamesAndEmptyValues() {
        assertThat(normalizer.normalizeNames(" Jane Doe ; ;John Doe;Jane Doe; Jane Doe "))
                .containsExactly("Jane Doe", "John Doe");
        assertThat(normalizer.normalizeNames(null)).isEmpty();
        assertThat(normalizer.normalizeNames(" ; ")).isEmpty();
    }

    @Test
    void parsesLocationsAndDiscardsOnlyMalformedItems() {
        var result = normalizer.normalizeLocations(
                "4#Exeter, Devon, United Kingdom#UK#UKD4#50.7#-3.53333#-2595805;"
                        + "1#Unknown#US#USCA###feature;bad;1#Broken#US#USCA#north#3#id");

        assertThat(result.locations()).containsExactly(
                new GkgLocation(4, "Exeter, Devon, United Kingdom", "UK", "UKD4",
                        50.7, -3.53333, "-2595805"),
                new GkgLocation(1, "Unknown", "US", "USCA", null, null, "feature"));
        assertThat(result.discardedCount()).isEqualTo(2);
    }

    @Test
    void parsesAllToneFieldsAndTreatsInvalidComponentsAsNull() {
        assertThat(normalizer.normalizeTone("-3.5,2.1,bad,,5.5,6.5,321"))
                .isEqualTo(new GkgTone(-3.5, 2.1, null, null, 5.5, 6.5, 321));
        assertThat(normalizer.normalizeTone(null))
                .isEqualTo(new GkgTone(null, null, null, null, null, null, null));
        assertThat(normalizer.normalizeTone("0,1,2,3,4,5,12.5").wordCount()).isNull();
        assertThat(normalizer.normalizeTone("NaN,Infinity,2,3,4,5,12"))
                .extracting(GkgTone::value, GkgTone::positiveScore)
                .containsExactly(null, null);
    }
}
