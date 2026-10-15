package com.example.globalnewsenginev1.stories.snapshot;

import com.example.globalnewsenginev1.stories.embedding.StoryEmbeddingService;
import com.example.globalnewsenginev1.stories.query.StoryDetail;
import com.example.globalnewsenginev1.stories.query.StoryPage;
import com.example.globalnewsenginev1.stories.query.StoryQueryService;
import com.example.globalnewsenginev1.stories.query.StorySummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryReleaseWorkflowTests {
    @TempDir
    Path temporary;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StoryEmbeddingService embeddings = mock(StoryEmbeddingService.class);
    private final StorySnapshotService snapshots = mock(StorySnapshotService.class);
    private final StoryPromotionService promotion = mock(StoryPromotionService.class);
    private final StoryRegressionEvaluation regressions = mock(StoryRegressionEvaluation.class);
    private final StoryQueryService stories = mock(StoryQueryService.class);
    private final ObjectMapper json = StoryReleaseWorkflow.objectMapper();

    @Test
    void precheckStopsBeforeExpensiveWork() {
        StoryReleaseWorkflow workflow = workflow(running());

        assertThatThrownBy(() -> workflow.run(request(false, null)))
                .hasMessageContaining("already unfinished");
        verify(embeddings, never()).processVersion(anyLong(), anyInt(), any());
        verify(snapshots, never()).processBackfill(anyLong(), any());
    }

    @Test
    void resumesPreparationAndWritesReviewEvidence() throws Exception {
        StoryReleaseWorkflow.Precheck open = precheck(2, 0, 0, true);
        StoryReleaseWorkflow.Precheck ready = precheck(0, 0, 0, false);
        StoryReleaseWorkflow workflow = workflow(open, ready, ready, ready);
        when(embeddings.processVersion(1L, 100, WATERMARK))
                .thenReturn(new StoryEmbeddingService.ProcessingResult(2, 2, 0));
        reviewed(true);

        StoryReleaseWorkflow.Evidence result = workflow.run(request(false, null));

        assertThat(result.outcome()).isEqualTo("REVIEWED");
        assertThat(temporary.resolve("evidence.json")).exists();
        verify(embeddings).processVersion(1L, 100, WATERMARK);
        verify(snapshots).processBackfill(1L, WATERMARK);
    }

    @Test
    void failedGatesAndMissingApprovalNeverPromote() {
        StoryReleaseWorkflow workflow = workflow(ready(), ready());
        reviewed(false);
        assertThat(workflow.run(request(true, null)).outcome()).isEqualTo("BLOCKED_GATES");
        verify(promotion, never()).promote(any(), any(), any());

        workflow = workflow(ready(), ready());
        reviewed(true);
        assertThat(workflow.run(request(true, null)).outcome()).isEqualTo("BLOCKED_APPROVAL");

        workflow = workflow(ready(), ready());
        reviewed(true);
        StoryPromotionService.Approval incomplete =
                new StoryPromotionService.Approval("owner", "", "approved");
        assertThat(workflow.run(request(true, incomplete)).outcome()).isEqualTo("BLOCKED_APPROVAL");
        verify(promotion, never()).promote(any(), any(), any());
    }

    @Test
    void successfulPromotionVerifiesListAndDetail() {
        StoryReleaseWorkflow workflow = workflow(ready(), ready());
        reviewed(true);
        successfulQueries();
        StoryPromotionService.Approval approval = approval();

        StoryReleaseWorkflow.Evidence result = workflow.run(request(true, approval));
        doReturn(active()).when(workflow).precheck("candidate-v1");
        StoryReleaseWorkflow.Evidence repeated = workflow.run(request(true, approval));

        assertThat(result.outcome()).isEqualTo("ACTIVE");
        assertThat(repeated).isEqualTo(result);
        assertThat(result.verification().visibleStories()).isEqualTo(1);
        verify(promotion).promote(any(), any(), eq(approval));
    }

    @Test
    void activeRerunRejectsChangedInputs() {
        StoryReleaseWorkflow workflow = workflow(ready(), ready());
        reviewed(true);
        StoryPromotionService.Approval approval = approval();
        successfulQueries();
        workflow.run(request(true, approval));
        doReturn(active()).when(workflow).precheck("candidate-v1");
        StoryReleaseWorkflow.Request original = request(true, approval);
        StoryReleaseWorkflow.Request changed = new StoryReleaseWorkflow.Request(
                original.candidateVersion(), original.holdout(), original.regressionCorpus(),
                new StoryReleaseWorkflow.ReviewMetadata("another-reviewer", "docs/review.md", "reviewed"),
                original.promote(), original.approval(), original.evidenceFile(),
                original.embeddingBatchSize(), original.maxEmbeddingBatches());

        assertThatThrownBy(() -> workflow.run(changed))
                .hasMessageContaining("inputs differ");
        verify(promotion).promote(any(), any(), eq(approval));
    }

    @Test
    void pendingEvidenceRepairsAfterPromotionWriteFailure() {
        StoryReleaseWorkflow workflow = workflow(ready(), ready());
        reviewed(true);
        successfulQueries();
        StoryPromotionService.Approval approval = approval();
        AtomicInteger writes = new AtomicInteger();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 2) throw new IllegalStateException("disk unavailable");
            return invocation.callRealMethod();
        }).when(workflow).write(any(), any(), any());

        assertThatThrownBy(() -> workflow.run(request(true, approval)))
                .hasMessageContaining("disk unavailable");
        doCallRealMethod().when(workflow).write(any(), any(), any());
        doReturn(active()).when(workflow).precheck("candidate-v1");

        StoryReleaseWorkflow.Evidence recovered = workflow.run(request(true, approval));

        assertThat(recovered.outcome()).isEqualTo("ACTIVE");
        assertThat(recovered.verification()).isNotNull();
        verify(promotion, times(1)).promote(any(), any(), eq(approval));
    }

    @Test
    void retiredCandidateDoesNotReuseHistoricalActiveEvidence() {
        StoryReleaseWorkflow workflow = workflow(ready(), ready());
        reviewed(true);
        successfulQueries();
        StoryPromotionService.Approval approval = approval();
        workflow.run(request(true, approval));
        doReturn(new StoryReleaseWorkflow.Precheck(1, "RETIRED", 2L, WATERMARK, WATERMARK,
                WATERMARK, false, 0, 0, 0, false)).when(workflow).precheck("candidate-v1");

        assertThatThrownBy(() -> workflow.run(request(true, approval)))
                .hasMessageContaining("Candidate must be SHADOW");
        verify(promotion).promote(any(), any(), eq(approval));
    }

    private StoryReleaseWorkflow workflow(StoryReleaseWorkflow.Precheck... checks) {
        StoryReleaseWorkflow workflow = spy(new StoryReleaseWorkflow(jdbc, embeddings, snapshots,
                promotion, regressions, stories));
        var prechecks = doReturn(checks[0]);
        for (int index = 1; index < checks.length; index++) prechecks.doReturn(checks[index]);
        prechecks.when(workflow).precheck("candidate-v1");
        when(snapshots.processBackfill(1L, WATERMARK))
                .thenReturn(new StorySnapshotService.ProcessingResult(1, 1, 0, 0));
        when(regressions.evaluate(eq(1L), any())).thenReturn(
                new StoryRegressionEvaluation.Result("regression-v1", "regression-hash", 1, 0,
                        List.of(), 1, List.of(), 0));
        return workflow;
    }

    private void reviewed(boolean passed) {
        var evaluation = new StoryReleaseEvaluation.Result("story-release-gates-v1", "hash",
                20, 20, passed ? 20 : 17, passed ? 0 : 3, passed ? 0 : 3,
                passed ? 1 : 0.85, passed ? 1 : 0.85, passed);
        var diff = new StoryPromotionService.Diff(1, 1, 0, 0, 2, 2, 0, 0, 0);
        when(promotion.review(eq(1L), any())).thenReturn(
                new StoryPromotionService.Review(1, 2L, "basis", diff, evaluation));
    }

    private StoryReleaseWorkflow.Request request(boolean promote,
                                                  StoryPromotionService.Approval approval) {
        var holdout = new StoryReleaseEvaluation.Holdout("holdout-v1", "source", "labeller",
                "independent", List.of());
        var metadata = new StoryReleaseWorkflow.ReviewMetadata(
                "reviewer", "docs/review.md", "reviewed unchanged candidate");
        return new StoryReleaseWorkflow.Request("candidate-v1", holdout, json.createObjectNode(),
                metadata, promote, approval, temporary.resolve("evidence.json"), 100, 5);
    }

    private StoryPromotionService.Approval approval() {
        return new StoryPromotionService.Approval("owner", "docs/review.md", "approved");
    }

    private void successfulQueries() {
        UUID id = UUID.randomUUID();
        StorySummary summary = mock(StorySummary.class);
        when(summary.id()).thenReturn(id);
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(1L));
        when(stories.stories(0, 1)).thenReturn(new StoryPage(List.of(summary), 0, 1, 1));
        when(stories.story(id)).thenReturn(Optional.of(mock(StoryDetail.class)));
    }

    private StoryReleaseWorkflow.Precheck running() {
        return new StoryReleaseWorkflow.Precheck(1, "SHADOW", 2L, WATERMARK, WATERMARK,
                WATERMARK, false, 0, 0, 0, true);
    }

    private StoryReleaseWorkflow.Precheck ready() {
        return precheck(0, 0, 0, false);
    }

    private StoryReleaseWorkflow.Precheck active() {
        return new StoryReleaseWorkflow.Precheck(1, "ACTIVE", 1L, WATERMARK, WATERMARK,
                WATERMARK, false, 0, 0, 0, false);
    }

    private StoryReleaseWorkflow.Precheck precheck(int missing, int stale, int incomplete,
                                                    boolean older) {
        return new StoryReleaseWorkflow.Precheck(1, "SHADOW", 2L, WATERMARK,
                older ? WATERMARK.minusSeconds(60) : WATERMARK, WATERMARK, older,
                missing, stale, incomplete, false);
    }

    private static final Instant WATERMARK = Instant.parse("2026-09-03T14:30:00Z");
}
