package com.example.globalnewsenginev1.ingestion;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

record GdeltCompleteWindow(Instant sourceTimestamp, Map<GdeltDataset, URI> files) {
}
