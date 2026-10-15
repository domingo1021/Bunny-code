package com.bunnycode.project.api;

import com.bunnycode.project.application.ProjectQueryService;
import com.bunnycode.project.application.ProjectQueryService.ProjectStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Headline numbers for the home page. Each module publishes its own under {@code /api/v2/stats}. */
@RestController
class ProjectStatsController {

    private final ProjectQueryService queries;

    ProjectStatsController(ProjectQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/v2/stats/projects")
    ProjectStats stats() {
        return queries.stats();
    }
}
