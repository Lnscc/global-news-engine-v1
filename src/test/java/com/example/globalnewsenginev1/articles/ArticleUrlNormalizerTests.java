package com.example.globalnewsenginev1.articles;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleUrlNormalizerTests {

    private final ArticleUrlNormalizer normalizer = new ArticleUrlNormalizer();

    @Test
    void removesTrackingFragmentDefaultPortAndSortsQuery() {
        NormalizedArticleUrl normalized = normalizer.normalize(
                " HTTPS://Example.ORG:443/news/a/?b=2&utm_source=x&a=1#comments ");

        assertThat(normalized.canonicalUrl()).isEqualTo("https://example.org/news/a?a=1&b=2");
        assertThat(normalized.domain()).isEqualTo("example.org");
        assertThat(normalized.urlHash()).hasSize(64);
    }

    @Test
    void keepsHttpAndHttpsSeparate() {
        NormalizedArticleUrl http = normalizer.normalize("http://example.org/news/a");
        NormalizedArticleUrl https = normalizer.normalize("https://example.org/news/a");

        assertThat(http.canonicalUrl()).isEqualTo("http://example.org/news/a");
        assertThat(https.canonicalUrl()).isEqualTo("https://example.org/news/a");
        assertThat(http.urlHash()).isNotEqualTo(https.urlHash());
    }

    @Test
    void preservesExistingPercentEncoding() {
        NormalizedArticleUrl normalized = normalizer.normalize("https://example.org/news/a%20b?x=a%2Bb");

        assertThat(normalized.canonicalUrl()).isEqualTo("https://example.org/news/a%20b?x=a%2Bb");
    }

    @Test
    void removesKnownTrackingParametersCaseInsensitively() {
        NormalizedArticleUrl normalized = normalizer.normalize(
                "https://example.org/news?GCLID=1&x=2&fbclid=3&UTM_medium=social");

        assertThat(normalized.canonicalUrl()).isEqualTo("https://example.org/news?x=2");
    }

    @Test
    void rejectsEmptyMalformedAndUnsupportedUrls() {
        assertThatThrownBy(() -> normalizer.normalize(" "))
                .isInstanceOf(ArticleUrlNormalizationException.class);
        assertThatThrownBy(() -> normalizer.normalize("not a url"))
                .isInstanceOf(ArticleUrlNormalizationException.class);
        assertThatThrownBy(() -> normalizer.normalize("ftp://example.org/news"))
                .isInstanceOf(ArticleUrlNormalizationException.class);
    }
}
