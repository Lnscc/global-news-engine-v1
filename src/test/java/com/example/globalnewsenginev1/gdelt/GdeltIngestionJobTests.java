package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.articles.ArticleProjectionJob;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltIngestionJobTests {

    @Test
    void downloadsAndParsesAvailableBatches() throws IOException, InterruptedException {
        GdeltDiscoveryJob discoveryJob = mock(GdeltDiscoveryJob.class);
        GdeltDownloadJob downloadJob = mock(GdeltDownloadJob.class);
        GdeltParseJob parseJob = mock(GdeltParseJob.class);
        GdeltEventNormalizationJob eventNormalizationJob = mock(GdeltEventNormalizationJob.class);
        GdeltMentionNormalizationJob mentionNormalizationJob = mock(GdeltMentionNormalizationJob.class);
        GdeltGkgNormalizationJob gkgNormalizationJob = mock(GdeltGkgNormalizationJob.class);
        GdeltBatchNormalizationStatusJob batchNormalizationStatusJob = mock(GdeltBatchNormalizationStatusJob.class);
        ArticleProjectionJob articleProjectionJob = mock(ArticleProjectionJob.class);

        when(downloadJob.runNextBatch()).thenReturn(true, true, true, false);
        when(parseJob.runNextBatch()).thenReturn(true, true, false);

        GdeltIngestionJob job = new GdeltIngestionJob(
                discoveryJob,
                downloadJob,
                parseJob,
                eventNormalizationJob,
                mentionNormalizationJob,
                gkgNormalizationJob,
                batchNormalizationStatusJob,
                articleProjectionJob
        );

        job.runIngestion();

        verify(discoveryJob).run();
        verify(downloadJob, times(4)).runNextBatch();
        verify(parseJob, times(3)).runNextBatch();
        verify(eventNormalizationJob).run();
        verify(mentionNormalizationJob).run();
        verify(gkgNormalizationJob).run();
        verify(articleProjectionJob).run();
        verify(batchNormalizationStatusJob).run();
    }
}
