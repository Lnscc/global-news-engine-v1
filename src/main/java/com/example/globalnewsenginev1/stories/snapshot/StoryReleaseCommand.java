package com.example.globalnewsenginev1.stories.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "stories.release.candidate-version")
class StoryReleaseCommand implements ApplicationRunner {
    private final StoryReleaseWorkflow workflow;
    private final ObjectMapper json = StoryReleaseWorkflow.objectMapper();
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    StoryReleaseCommand(StoryReleaseWorkflow workflow, Environment environment,
                        ConfigurableApplicationContext context) {
        this.workflow = workflow;
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        try {
            String candidate = required("stories.release.candidate-version");
            Path holdoutFile = Path.of(required("stories.release.holdout"));
            Path regressionFile = Path.of(environment.getProperty("stories.release.regression",
                    "docs/analysis/ART-045-regression-cases-v1.json"));
            Path evidenceFile = Path.of(environment.getProperty("stories.release.evidence",
                    "docs/analysis/" + candidate + "-release-evidence.json"));
            boolean promote = environment.getProperty("stories.release.promote", Boolean.class, false);
            StoryPromotionService.Approval approval = approval();
            workflow.run(new StoryReleaseWorkflow.Request(candidate,
                    json.readValue(holdoutFile.toFile(), StoryReleaseEvaluation.Holdout.class),
                    json.readTree(regressionFile.toFile()),
                    new StoryReleaseWorkflow.ReviewMetadata(
                            required("stories.release.reviewed-by"),
                            required("stories.release.review-document"),
                            required("stories.release.review-reason")),
                    promote, approval, evidenceFile,
                    environment.getProperty("stories.release.embedding-batch-size", Integer.class, 100),
                    environment.getProperty("stories.release.max-embedding-batches", Integer.class, 1000)));
        } finally {
            context.close();
        }
    }

    private StoryPromotionService.Approval approval() {
        String approvedBy = environment.getProperty("stories.release.approved-by");
        String document = environment.getProperty("stories.release.approval-document");
        String reason = environment.getProperty("stories.release.approval-reason");
        if (approvedBy == null && document == null && reason == null) return null;
        return new StoryPromotionService.Approval(approvedBy, document, reason);
    }

    private String required(String property) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(property + " is required");
        return value;
    }
}
