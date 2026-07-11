package com.example.globalnewsenginev1.articles.enrichment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Component
class ArticleHtmlParser {
    ArticleEnrichmentResult parse(CrawledArticlePage page) throws ArticleCrawlException {
        Document document = Jsoup.parse(page.html(), page.finalUri().toString());
        String title = firstContent(document, "meta[property=og:title]", "meta[name=twitter:title]");
        if (title == null) title = clean(document.title());

        Instant publishedAt = parseInstant(firstContent(document, "meta[property=article:published_time]",
                "meta[name=article:published_time]", "meta[name=date]", "meta[name=pubdate]",
                "meta[itemprop=datePublished]"));
        if (publishedAt == null) {
            Element time = document.selectFirst("time[datetime]");
            publishedAt = parseInstant(time == null ? null : time.attr("datetime"));
        }

        String language = clean(document.select("html").attr("lang"));
        if (language == null) language = firstContent(document, "meta[http-equiv=content-language]", "meta[name=language]");

        String image = firstContent(document, "meta[property=og:image]", "meta[name=twitter:image]");
        if (image != null) {
            try { image = page.finalUri().resolve(image).toString(); }
            catch (IllegalArgumentException ignored) { image = null; }
        }

        Document visible = document.clone();
        visible.select("script,style,noscript,nav,header,footer,aside,form,svg,canvas").remove();
        Element main = visible.selectFirst("article,main,[role=main]");
        String text = clean((main == null ? visible.body() : main).text());
        if (title == null && text == null) throw new ArticleCrawlException("UNUSABLE_HTML", "HTML contains no usable content", false);
        return new ArticleEnrichmentResult(title, publishedAt, language, image, text);
    }

    private String firstContent(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);
            String value = element == null ? null : clean(element.attr("content"));
            if (value != null) return value;
        }
        return null;
    }

    private Instant parseInstant(String value) {
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (DateTimeParseException ignored) {
            try { return OffsetDateTime.parse(value).toInstant(); }
            catch (DateTimeParseException alsoIgnored) { return null; }
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
