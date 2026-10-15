package com.bunnycode.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.bunnycode.project.application.ProjectCommandService;
import com.bunnycode.project.application.ProjectQueryService;
import com.bunnycode.project.application.ProjectReference;
import com.bunnycode.project.application.ProjectSort;
import com.bunnycode.project.application.RecordingService;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.security.WebSecurityTestConfiguration;
import com.bunnycode.shared.security.WebSecurityTestConfiguration.TestTokens;
import com.bunnycode.shared.web.PageResponse;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest({ProjectController.class, UserProjectsController.class, RecordingController.class})
@ActiveProfiles("test")
@Import(WebSecurityTestConfiguration.class)
@DisplayName("Project HTTP API")
class ProjectControllerTest {

    private static final String NEW_PROJECT = """
            {"name":"bunny","description":"A big bunny","visibility":"PUBLIC","versionName":"main","fileName":"main.js"}""";

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private TestTokens tokens;
    @MockitoBean
    private ProjectQueryService queries;
    @MockitoBean
    private ProjectCommandService commands;
    @MockitoBean
    private RecordingService recordings;

    @Nested
    @DisplayName("GET /api/v2/projects")
    class Listing {

        @Test
        @DisplayName("is public and defaults to the 6 most recent")
        void defaults() {
            given(queries.publicProjects(isNull(), eq(ProjectSort.RECENT), eq(0), eq(6)))
                    .willReturn(new PageResponse<>(List.of(), 0, 6, 0, 0));

            assertThat(mvc.get().uri("/api/v2/projects")).hasStatusOk()
                    .bodyJson().extractingPath("$.size").isEqualTo(6);
        }

        @Test
        @DisplayName("rejects page sizes over 50 before reaching the service")
        void boundsPageSize() {
            assertThat(mvc.get().uri("/api/v2/projects?size=500")).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("request.invalid");
            then(queries).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("POST /api/v2/projects")
    class Creating {

        @Test
        @DisplayName("Anonymous → 401")
        void anonymous() {
            assertThat(mvc.post().uri("/api/v2/projects").contentType(MediaType.APPLICATION_JSON).content(NEW_PROJECT))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Bearer token → 201 with a Location header; the owner is the caller, not a request field")
        void created() {
            given(commands.create(eq(new AuthenticatedUser(1L, "bunny")), any())).willReturn(new ProjectReference(10L, "bunny"));

            assertThat(mvc.post().uri("/api/v2/projects").header("Authorization", tokens.bearer(1L, "bunny"))
                    .contentType(MediaType.APPLICATION_JSON).content(NEW_PROJECT))
                    .hasStatus(HttpStatus.CREATED)
                    .hasHeader("Location", "/api/v2/projects/bunny");
        }

        @Test
        @DisplayName("Invalid names are rejected with the shared naming rule")
        void invalidName() {
            assertThat(mvc.post().uri("/api/v2/projects").header("Authorization", tokens.bearer(1L, "bunny"))
                    .contentType(MediaType.APPLICATION_JSON).content(NEW_PROJECT.replace("\"bunny\"", "\"bun ny!\"")))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
        }

        @Test
        @DisplayName("Version names may contain spaces and descriptions may be empty (the web client's defaults)")
        void relaxedNames() {
            given(commands.create(any(), any())).willReturn(new ProjectReference(10L, "bunny"));

            assertThat(mvc.post().uri("/api/v2/projects").header("Authorization", tokens.bearer(1L, "bunny"))
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"name":"bunny","description":"","visibility":"PUBLIC","versionName":"version 1","fileName":"index.js"}"""))
                    .hasStatus(HttpStatus.CREATED);
            assertThat(mvc.post().uri("/api/v2/projects").header("Authorization", tokens.bearer(1L, "bunny"))
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"name":"bunny","description":"","visibility":"PUBLIC","versionName":" padded","fileName":"index.js"}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.errors[0].field").isEqualTo("versionName");
        }

        @Test
        @DisplayName("Session cookie without a CSRF token → 403 (cookie auth must be CSRF-protected)")
        void cookieWithoutCsrf() {
            assertThat(mvc.post().uri("/api/v2/projects").cookie(new Cookie("bunny_session", tokens.forUser(1L, "bunny")))
                    .contentType(MediaType.APPLICATION_JSON).content(NEW_PROJECT))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson().extractingPath("$.code").isEqualTo("auth.csrf");
        }

        @Test
        @DisplayName("Session cookie with a CSRF token → 201")
        void cookieWithCsrf() {
            given(commands.create(any(), any())).willReturn(new ProjectReference(10L, "bunny"));

            assertThat(mvc.post().uri("/api/v2/projects").cookie(new Cookie("bunny_session", tokens.forUser(1L, "bunny")))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(NEW_PROJECT))
                    .hasStatus(HttpStatus.CREATED);
        }
    }

    @Test
    @DisplayName("POST /views is open to anonymous visitors and keyed by address")
    void anonymousView() {
        assertThat(mvc.post().uri("/api/v2/projects/bunny/views")).hasStatus(HttpStatus.NO_CONTENT);
        then(commands).should().recordView(eq("bunny"), eq(java.util.Optional.empty()), any());
    }

    @Test
    @DisplayName("PUT /star requires sign-in")
    void starNeedsAuth() {
        assertThat(mvc.put().uri("/api/v2/projects/bunny/star")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.put().uri("/api/v2/projects/bunny/star").header("Authorization", tokens.bearer(2L, "fan")))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    @Nested
    @DisplayName("POST …/recording")
    class SavingRecordings {

        @Test
        @DisplayName("rejects an empty session and unknown action names")
        void validates() {
            String uri = "/api/v2/projects/bunny/versions/1/recording";
            assertThat(mvc.post().uri(uri).header("Authorization", tokens.bearer(1L, "bunny"))
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"events":[],"code":""}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(mvc.post().uri(uri).header("Authorization", tokens.bearer(1L, "bunny"))
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"events":[{"action":"drop table","line":0,"index":0,"code":"","timestamp":1}],"code":""}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST);
            then(recordings).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("anonymous callers cannot save")
        void needsAuth() {
            assertThat(mvc.post().uri("/api/v2/projects/bunny/versions/1/recording").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"events":[{"action":"create","line":0,"index":0,"code":"a","timestamp":1}],"code":"a"}"""))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    @DisplayName("GET /api/v2/users/{id}/projects is public and validates paging")
    void userProjects() {
        given(queries.projectsOf(eq(3L), any(), isNull(), anyInt(), anyInt())).willReturn(new PageResponse<>(List.of(), 0, 6, 0, 0));

        assertThat(mvc.get().uri("/api/v2/users/3/projects")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v2/users/3/projects?page=-1")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
