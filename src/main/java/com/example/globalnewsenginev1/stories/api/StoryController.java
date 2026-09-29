package com.example.globalnewsenginev1.stories.api;

import com.example.globalnewsenginev1.stories.query.StoryDetail;
import com.example.globalnewsenginev1.stories.query.StoryPage;
import com.example.globalnewsenginev1.stories.query.StoryQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/stories")
public class StoryController {

    private static final Set<String> LIST_PARAMETERS = Set.of("offset", "limit");

    private final StoryQueryService queryService;

    public StoryController(StoryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public StoryPage stories(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam Map<String, String> parameters
    ) {
        parameters.keySet().stream()
                .filter(parameter -> !LIST_PARAMETERS.contains(parameter))
                .findFirst()
                .ifPresent(parameter -> {
                    throw new IllegalArgumentException("unknown parameter: " + parameter);
                });
        return queryService.stories(offset, limit);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoryDetail> story(@PathVariable UUID id) {
        return queryService.story(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
