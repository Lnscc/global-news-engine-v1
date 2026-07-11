package com.example.globalnewsenginev1.articles.enrichment;

import java.net.URI;

record CrawledArticlePage(URI finalUri, String html) {
}
