package com.example.globalnewsenginev1.gdelt.discovery;

import com.example.globalnewsenginev1.gdelt.GdeltDataset;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record GdeltCompleteWindow(Instant sourceTimestamp, Map<GdeltDataset, URI> files) {
}
