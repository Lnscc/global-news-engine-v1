package com.example.globalnewsenginev1.gdelt.retention;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltPayloadRetentionJobTests {

    @Test
    void runsBoundedBatchesUntilNoMorePayloadsAreDeleted() {
        GdeltPayloadRetentionService service = mock(GdeltPayloadRetentionService.class);
        when(service.cleanupBatch(any(Instant.class), eq(2)))
                .thenReturn(new GdeltPayloadRetentionResult(2, 1, 1))
                .thenReturn(new GdeltPayloadRetentionResult(0, 0, 0));
        GdeltPayloadRetentionJob job = new GdeltPayloadRetentionJob(service, Duration.ofDays(7), 2, 10);

        job.cleanupPayloads();

        verify(service, times(2)).cleanupBatch(any(Instant.class), eq(2));
    }

    @Test
    void rejectsUnboundedOrInvalidConfiguration() {
        GdeltPayloadRetentionService service = mock(GdeltPayloadRetentionService.class);

        assertThatThrownBy(() -> new GdeltPayloadRetentionJob(service, Duration.ofDays(-1), 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GdeltPayloadRetentionJob(service, Duration.ofDays(7), 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GdeltPayloadRetentionJob(service, Duration.ofDays(7), 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
