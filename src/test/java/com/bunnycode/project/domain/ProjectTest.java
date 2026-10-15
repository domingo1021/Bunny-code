package com.bunnycode.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bunnycode.project.domain.Project.NewProject;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.error.ErrorKind;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Project aggregate")
class ProjectTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
    private static final long OWNER = 7L;

    private static Project newProject(Visibility visibility) {
        return Project.create(new NewProject("bunny", "A big bunny", visibility, "main", "main.js"),
                OWNER, "/record/template.js", NOW);
    }

    @Nested
    @DisplayName("When a project is created")
    class Creation {

        @Test
        @DisplayName("then it has version 1 whose file is the template")
        void startsWithVersionOne() {
            Project project = newProject(Visibility.PUBLIC);

            assertThat(project.getVersions()).singleElement().satisfies(version -> {
                assertThat(version.getNumber()).isEqualTo(1);
                assertThat(version.getName()).isEqualTo("main");
                assertThat(version.currentFile()).get().satisfies(file -> {
                    assertThat(file.getName()).isEqualTo("main.js");
                    assertThat(file.getStorageKey()).isEqualTo("/record/template.js");
                    assertThat(file.getRevision()).isEqualTo(String.valueOf(NOW.toEpochMilli()));
                });
            });
            assertThat(project.getCreatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("When a version is branched")
    class Branching {

        @Test
        @DisplayName("then it gets the next number and a copy of the latest version's current file")
        void branchesFromLatest() {
            Project project = newProject(Visibility.PUBLIC);
            project.getVersions().getFirst().saveNewRevision("main.js", "/record/v1-saved.js", "2");

            ProjectVersion second = project.branchVersion("feature", "feature.js", NOW.plusSeconds(60));
            ProjectVersion third = project.branchVersion("third", "third.js", NOW.plusSeconds(120));

            assertThat(second.getNumber()).isEqualTo(2);
            assertThat(second.currentFile()).get().extracting(ProjectFile::getStorageKey).isEqualTo("/record/v1-saved.js");
            assertThat(third.getNumber()).isEqualTo(3);
            assertThat(project.findVersion(3)).contains(third);
        }

        @Test
        @DisplayName("Given a version with the same name exists, then it conflicts")
        void rejectsDuplicateName() {
            Project project = newProject(Visibility.PUBLIC);

            assertThatThrownBy(() -> project.branchVersion("main", "x.js", NOW))
                    .isInstanceOfSatisfying(BunnyException.class, e -> {
                        assertThat(e.kind()).isEqualTo(ErrorKind.CONFLICT);
                        assertThat(e.code()).isEqualTo("version.name-taken");
                    });
        }
    }

    @Test
    @DisplayName("Saving a new revision supersedes the previous file instead of deleting it")
    void savingSupersedes() {
        ProjectVersion version = newProject(Visibility.PUBLIC).getVersions().getFirst();
        ProjectFile original = version.currentFile().orElseThrow();

        ProjectFile saved = version.saveNewRevision("main.js", "/record/new.js", "99");

        assertThat(original.isSuperseded()).isTrue();
        assertThat(version.currentFile()).contains(saved);
        assertThat(version.getFiles()).hasSize(2);
    }

    @Nested
    @DisplayName("Visibility")
    class VisibilityRules {

        @Test
        @DisplayName("Public projects are visible to everyone, including anonymous visitors")
        void publicIsVisible() {
            Project project = newProject(Visibility.PUBLIC);
            assertThat(project.isVisibleTo(Optional.empty())).isTrue();
            assertThat(project.isVisibleTo(Optional.of(99L))).isTrue();
        }

        @Test
        @DisplayName("Private projects are visible to their owner only")
        void privateIsOwnerOnly() {
            Project project = newProject(Visibility.PRIVATE);
            assertThat(project.isVisibleTo(Optional.of(OWNER))).isTrue();
            assertThat(project.isVisibleTo(Optional.of(99L))).isFalse();
            assertThat(project.isVisibleTo(Optional.empty())).isFalse();
        }
    }
}
