package com.bunnycode.project.api;

import com.bunnycode.project.application.ProjectQueryService;
import com.bunnycode.project.application.ProjectSummaryView;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.security.CurrentUser;
import com.bunnycode.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class UserProjectsController {

    private final ProjectQueryService queries;

    UserProjectsController(ProjectQueryService queries) {
        this.queries = queries;
    }

    /** Owners see their private projects too; everyone else sees public ones only. */
    @GetMapping("/api/v2/users/{userId}/projects")
    PageResponse<ProjectSummaryView> projectsOf(
            @PathVariable @Positive long userId,
            @CurrentUser Optional<AuthenticatedUser> viewer,
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "6") @Min(1) @Max(50) int size) {
        return queries.projectsOf(userId, viewer, keyword, page, size);
    }
}
