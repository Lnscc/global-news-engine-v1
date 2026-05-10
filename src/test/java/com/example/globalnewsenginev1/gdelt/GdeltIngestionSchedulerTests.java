package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.articles.ArticleProjectionJob;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltIngestionSchedulerTests {

    @Test
    void downloadsAndParsesMultipleBatchesPerRun() throws IOException, InterruptedException {
        GdeltDiscoveryJob discoveryJob = mock(GdeltDiscoveryJob.class);
        GdeltDownloadJob downloadJob = mock(GdeltDownloadJob.class);
        GdeltParseJob parseJob = mock(GdeltParseJob.class);
        GdeltEventNormalizationJob eventNormalizationJob = mock(GdeltEventNormalizationJob.class);
        GdeltMentionNormalizationJob mentionNormalizationJob = mock(GdeltMentionNormalizationJob.class);
        GdeltGkgNormalizationJob gkgNormalizationJob = mock(GdeltGkgNormalizationJob.class);
        GdeltBatchNormalizationStatusJob batchNormalizationStatusJob = mock(GdeltBatchNormalizationStatusJob.class);
        ArticleProjectionJob articleProjectionJob = mock(ArticleProjectionJob.class);

        when(downloadJob.runNextBatch()).thenReturn(true, true, true);
        when(parseJob.runNextBatch()).thenReturn(true, true, false);

        GdeltIngestionScheduler scheduler = new GdeltIngestionScheduler(
                discoveryJob,
                downloadJob,
                parseJob,
                eventNormalizationJob,
                mentionNormalizationJob,
                gkgNormalizationJob,
                batchNormalizationStatusJob,
                articleProjectionJob,
                3
        );

        scheduler.discoverBatches();

        verify(discoveryJob).run();
        verify(downloadJob, times(3)).runNextBatch();
        verify(parseJob, times(3)).runNextBatch();
        verify(eventNormalizationJob, times(3)).run();
        verify(mentionNormalizationJob, times(3)).run();
        verify(gkgNormalizationJob, times(3)).run();
        verify(articleProjectionJob).run();
        verify(batchNormalizationStatusJob).run();
    }
}
