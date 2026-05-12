package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.gdelt.model.GdeltEvent;
import com.example.globalnewsenginev1.gdelt.normalization.GdeltEventNormalizationJob;
import com.example.globalnewsenginev1.gdelt.parser.GdeltEventParser;
import com.example.globalnewsenginev1.gdelt.repository.GdeltEventRepository;
import com.example.globalnewsenginev1.ingestion.RawSourceFile;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.StagingRow;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltEventNormalizationJobTests {

    @Test
    void normalizesUnprocessedEventRows() {
        GdeltEventRepository eventRepository = mock(GdeltEventRepository.class);
        SourceBatch batch = new SourceBatch("GDELT", "20260506123000");
        batch.putFile("EVENTS", "http://example.test/events.zip", 1, "abc");
        RawSourceFile file = batch.findFile("EVENTS").orElseThrow();
        StagingRow row = new StagingRow(batch, file, 1, GdeltEventParserTests.eventLine());

        when(eventRepository.findUnnormalizedRows("EVENTS")).thenReturn(List.of(row));
        when(eventRepository.findByGlobalEventId(123456789L)).thenReturn(Optional.empty());
        when(eventRepository.save(any(GdeltEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GdeltEventNormalizationJob job = new GdeltEventNormalizationJob(
                eventRepository,
                new GdeltEventParser()
        );

        int normalized = job.run();

        assertThat(normalized).isEqualTo(1);
        assertThat(row.getNormalizedAt()).isNotNull();
        assertThat(row.getNormalizationSkippedAt()).isNull();
        verify(eventRepository).save(any(GdeltEvent.class));
    }

    @Test
    void skipsInvalidRows() {
        GdeltEventRepository eventRepository = mock(GdeltEventRepository.class);
        SourceBatch batch = new SourceBatch("GDELT", "20260506123000");
        batch.putFile("EVENTS", "http://example.test/events.zip", 1, "abc");
        RawSourceFile file = batch.findFile("EVENTS").orElseThrow();
        StagingRow row = new StagingRow(batch, file, 1, "invalid");

        when(eventRepository.findUnnormalizedRows("EVENTS")).thenReturn(List.of(row));

        GdeltEventNormalizationJob job = new GdeltEventNormalizationJob(
                eventRepository,
                new GdeltEventParser()
        );

        int normalized = job.run();

        assertThat(normalized).isZero();
        assertThat(row.getNormalizedAt()).isNull();
        assertThat(row.getNormalizationSkippedAt()).isNotNull();
        verify(eventRepository, never()).save(any(GdeltEvent.class));
    }

    @Test
    void marksRowsWithExistingNormalizedEventAsNormalized() {
        GdeltEventRepository eventRepository = mock(GdeltEventRepository.class);
        SourceBatch batch = new SourceBatch("GDELT", "20260506123000");
        batch.putFile("EVENTS", "http://example.test/events.zip", 1, "abc");
        RawSourceFile file = batch.findFile("EVENTS").orElseThrow();
        StagingRow row = new StagingRow(batch, file, 1, GdeltEventParserTests.eventLine());

        when(eventRepository.findUnnormalizedRows("EVENTS")).thenReturn(List.of(row));
        when(eventRepository.existsByStagingRow(row)).thenReturn(true);

        GdeltEventNormalizationJob job = new GdeltEventNormalizationJob(
                eventRepository,
                new GdeltEventParser()
        );

        int normalized = job.run();

        assertThat(normalized).isEqualTo(1);
        assertThat(row.getNormalizedAt()).isNotNull();
        assertThat(row.getNormalizationSkippedAt()).isNull();
        verify(eventRepository, never()).save(any(GdeltEvent.class));
    }
}
