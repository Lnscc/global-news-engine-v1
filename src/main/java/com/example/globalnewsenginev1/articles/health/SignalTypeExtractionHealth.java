package com.example.globalnewsenginev1.articles.health;

import java.time.Instant;
import java.util.List;

public record SignalTypeExtractionHealth(
        String signalType,
        long pendingStageRows,
        long articleSignals,
        Instant latestProcessedSourceTimestamp,
        List<ExtractionErrorCount> extractionErrors
) {
}
