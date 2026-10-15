package com.bunnycode.project.api;

import com.bunnycode.project.application.ProjectCommandService;
import com.bunnycode.project.application.ProjectDetailView;
import com.bunnycode.project.application.ProjectQueryService;
import com.bunnycode.project.application.ProjectReference;
import com.bunnycode.project.application.ProjectSort;
import com.bunnycode.project.application.ProjectSummaryView;
import com.bunnycode.project.application.VersionView;
import com.bunnycode.project.domain.Project.NewProject;
import com.bunnycode.project.domain.Visibility;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.security.CurrentUser;
import com.bunnycode.shared.validation.ResourceName;
import com.bunnycode.shared.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v2/projects")
class ProjectController {

    private final ProjectQueryService queries;
    private final ProjectCommandService commands;

    ProjectController(ProjectQueryService queries, ProjectCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping
    PageResponse<ProjectSummaryView> list(
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(defaultValue = "RECENT") ProjectSort sort,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "6") @Min(1) @Max(50) int size) {
        return queries.publicProjects(keyword, sort, page, size);
    }

    @GetMapping("/{projectName}")
    ProjectDetailView detail(@PathVariable String projectName, @CurrentUser Optional<AuthenticatedUser> viewer) {
        return queries.detail(projectName, viewer);
    }

    @PostMapping
    ResponseEntity<ProjectReference> create(@CurrentUser AuthenticatedUser owner,
                                            @Valid @RequestBody CreateProjectRequest request) {
        ProjectReference created = commands.create(owner, request.toSpec());
        URI location = UriComponentsBuilder.fromPath("/api/v2/projects/{name}").build(created.name());
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/{projectName}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    VersionView createVersion(@PathVariable String projectName, @CurrentUser AuthenticatedUser user,
                              @Valid @RequestBody CreateVersionRequest request) {
        return commands.branchVersion(user, projectName, request.versionName(), request.fileName());
    }

    @PostMapping("/{projectName}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void recordView(@PathVariable String projectName, @CurrentUser Optional<AuthenticatedUser> viewer,
                    HttpServletRequest request) {
        commands.recordView(projectName, viewer, request.getRemoteAddr());
    }

    @PutMapping("/{projectName}/star")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void star(@PathVariable String projectName, @CurrentUser AuthenticatedUser user) {
        commands.star(user, projectName);
    }

    @DeleteMapping("/{projectName}/star")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unstar(@PathVariable String projectName, @CurrentUser AuthenticatedUser user) {
        commands.unstar(user, projectName);
    }

    record CreateProjectRequest(
            @ResourceName String name,
            @NotNull @Size(max = 50, message = "Descriptions can be up to 50 characters.") String description,
            @NotNull(message = "Please choose PUBLIC or PRIVATE.") Visibility visibility,
            @VersionName String versionName,
            @ResourceName String fileName) {

        NewProject toSpec() {
            return new NewProject(name, description.strip(), visibility, versionName, fileName);
        }
    }

    record CreateVersionRequest(@VersionName String versionName, @ResourceName String fileName) {
    }
}
