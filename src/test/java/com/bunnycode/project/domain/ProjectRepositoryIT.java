package com.bunnycode.project.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.MySqlTestConfiguration;
import com.bunnycode.project.domain.ProjectRows.ProjectSummaryRow;
import com.bunnycode.project.domain.ProjectRows.VersionFileRow;
import com.bunnycode.shared.web.LikePatterns;
import com.bunnycode.support.QueryCounter;
import com.bunnycode.support.TestData;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestConfiguration.class)
@DisplayName("Project repositories (MySQL)")
class ProjectRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ProjectRepository projects;
    @Autowired
    private ProjectVersionRepository versions;
    @Autowired
    private RecordingRepository recordings;
    @Autowired
    private ProjectStarRepository stars;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private TestData data;
    private long alice;
    private long bob;

    @BeforeEach
    void seed() {
        data = new TestData(jdbc);
        data.clear();
        alice = data.user("alice");
        bob = data.user("bob");
    }

    @Nested
    @DisplayName("Public feed")
    class Feed {

        @Test
        @DisplayName("lists only public projects, newest first, paged in SQL with an exact total")
        void recentFirst() {
            for (int i = 0; i < 8; i++) {
                data.project("public_" + i, alice, true, T0.plusSeconds(i));
            }
            data.project("secret", alice, false, T0.plusSeconds(100));

            Page<ProjectSummaryRow> first = projects.findRecent(Visibility.PUBLIC, PageRequest.of(0, 6));
            Page<ProjectSummaryRow> second = projects.findRecent(Visibility.PUBLIC, PageRequest.of(1, 6));

            assertThat(first.getContent()).extracting(ProjectSummaryRow::name)
                    .containsExactly("public_7", "public_6", "public_5", "public_4", "public_3", "public_2");
            assertThat(second.getContent()).extracting(ProjectSummaryRow::name).containsExactly("public_1", "public_0");
            assertThat(first.getTotalElements()).isEqualTo(8);
            assertThat(first.getTotalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("search treats % and _ literally and also matches owners resolved beforehand")
        void search() {
            data.project("100pct", alice, true, T0);
            data.project("bobs_thing", bob, true, T0.plusSeconds(1));
            data.project("other", bob, true, T0.plusSeconds(2));

            assertThat(projects.search(Visibility.PUBLIC, LikePatterns.contains("%"), Set.of(), PageRequest.of(0, 10)))
                    .isEmpty();
            assertThat(projects.search(Visibility.PUBLIC, LikePatterns.contains("s_t"), Set.of(), PageRequest.of(0, 10)))
                    .extracting(ProjectSummaryRow::name).containsExactly("bobs_thing");
            assertThat(projects.search(Visibility.PUBLIC, LikePatterns.contains("zzz"), Set.of(bob), PageRequest.of(0, 10)))
                    .extracting(ProjectSummaryRow::name).containsExactly("other", "bobs_thing");
        }

        @Test
        @DisplayName("each summary carries the name of the project's latest version, from the same query")
        void latestVersionName() {
            long project = data.project("versioned", alice, true, T0);
            data.version(project, "first", 1);
            data.version(project, "second take", 2);
            data.project("empty", alice, true, T0.minusSeconds(1));
            QueryCounter counter = new QueryCounter(entityManagerFactory);
            Page<ProjectSummaryRow>[] page = new Page[1];

            long statements = counter.count(() -> page[0] = projects.findRecent(Visibility.PUBLIC, PageRequest.of(0, 6)));

            assertThat(page[0].getContent()).extracting(ProjectSummaryRow::latestVersionName)
                    .containsExactly("second take", null);
            // One statement: the subquery runs inside it, and Spring Data skips the count query
            // because a first page that is not full already tells it the total.
            assertThat(statements).isEqualTo(1);
        }

        @Test
        @DisplayName("starred orders by star count")
        void starred() {
            long few = data.project("few_stars", alice, true, T0);
            long many = data.project("many_stars", alice, true, T0);
            jdbc.update("update project set star_count = 9 where project_id = ?", many);
            jdbc.update("update project set star_count = 1 where project_id = ?", few);

            assertThat(projects.findStarred(Visibility.PUBLIC, PageRequest.of(0, 3)))
                    .extracting(ProjectSummaryRow::name).containsExactly("many_stars", "few_stars");
        }

        @Test
        @DisplayName("popular orders by watch count")
        void popular() {
            long quiet = data.project("quiet", alice, true, T0);
            long loud = data.project("loud", alice, true, T0);
            jdbc.update("update project set watch_count = 50 where project_id = ?", loud);
            jdbc.update("update project set watch_count = 5 where project_id = ?", quiet);

            assertThat(projects.findPopular(Visibility.PUBLIC, PageRequest.of(0, 3)))
                    .extracting(ProjectSummaryRow::name).containsExactly("loud", "quiet");
        }
    }

    @Test
    @DisplayName("an owner's listing can include private projects; others see public ones only")
    void ownedBy() {
        data.project("mine_public", alice, true, T0);
        data.project("mine_private", alice, false, T0.plusSeconds(1));

        assertThat(projects.findOwnedBy(alice, EnumSet.allOf(Visibility.class), "%", PageRequest.of(0, 10)))
                .hasSize(2);
        assertThat(projects.findOwnedBy(alice, EnumSet.of(Visibility.PUBLIC), "%", PageRequest.of(0, 10)))
                .extracting(ProjectSummaryRow::name).containsExactly("mine_public");
    }

    @Test
    @DisplayName("soft-deleted projects are invisible to every query")
    void softDeleted() {
        long gone = data.project("gone", alice, true, T0);
        jdbc.update("update project set deleted = 1 where project_id = ?", gone);

        assertThat(projects.findByName("gone")).isEmpty();
        assertThat(projects.findRecent(Visibility.PUBLIC, PageRequest.of(0, 6))).isEmpty();
    }

    @Nested
    @DisplayName("Versions and their files — without N+1")
    class VersionsWithoutNPlusOne {

        @Test
        @DisplayName("every version with its current file comes back from ONE statement, however many versions there are")
        void oneStatementForAllVersions() {
            long few = projectWithVersions("few", 1);
            long many = projectWithVersions("many", 25);
            QueryCounter counter = new QueryCounter(entityManagerFactory);

            long forOne = counter.count(() -> versions.findVersionFiles(few));
            List<VersionFileRow>[] rows = new List[1];
            long forMany = counter.count(() -> rows[0] = versions.findVersionFiles(many));

            assertThat(forOne).isEqualTo(1);
            assertThat(forMany).isEqualTo(1);
            assertThat(rows[0]).hasSize(25).allSatisfy(row -> assertThat(row.fileKey()).endsWith("current.js"));
        }

        @Test
        @DisplayName("recordings of many versions are fetched with one IN query")
        void recordingsInOneStatement() {
            long project = projectWithVersions("recorded", 10);
            List<Long> versionIds = jdbc.queryForList("select version_id from version where project_id = ?", Long.class, project);
            versionIds.forEach(id -> data.recording(id, T0, T0.plusSeconds(60)));
            QueryCounter counter = new QueryCounter(entityManagerFactory);

            long statements = counter.count(() -> assertThat(recordings.findRowsByVersionIds(versionIds)).hasSize(10));

            assertThat(statements).isEqualTo(1);
        }

        @Test
        @DisplayName("access check answers ownership and 'already recorded' in one statement")
        void accessCheck() {
            long project = projectWithVersions("access", 1);
            long versionId = jdbc.queryForObject("select version_id from version where project_id = ?", Long.class, project);

            assertThat(versions.findAccess(versionId)).get().satisfies(row -> {
                assertThat(row.ownerId()).isEqualTo(alice);
                assertThat(row.recorded()).isFalse();
            });
            data.recording(versionId, T0, T0);
            assertThat(versions.findAccess(versionId)).get().extracting(ProjectRows.VersionAccessRow::recorded).isEqualTo(true);
        }

        private long projectWithVersions(String name, int count) {
            long project = data.project(name, alice, true, T0);
            for (int number = 1; number <= count; number++) {
                long version = data.version(project, "v" + number, number);
                data.file(version, "main.js", "/record/old.js", true);
                data.file(version, "main.js", "/record/v" + number + "-current.js", false);
            }
            return project;
        }
    }

    @Test
    @DisplayName("counters are incremented atomically in SQL")
    void atomicCounters() {
        long id = data.project("counted", alice, true, T0);

        projects.incrementWatchCount(id);
        projects.incrementWatchCount(id);
        projects.adjustStarCount(id, 1);

        assertThat(jdbc.queryForMap("select watch_count, star_count from project where project_id = ?", id))
                .containsEntry("watch_count", 2L).containsEntry("star_count", 1L);
    }

    @Test
    @DisplayName("starring is idempotent: the second insert reports 0 rows so the counter moves once")
    void idempotentStars() {
        long id = data.project("starred", alice, true, T0);

        assertThat(stars.insertIfAbsent(id, bob, T0)).isEqualTo(1);
        assertThat(stars.insertIfAbsent(id, bob, T0)).isZero();
        assertThat(stars.existsById(new ProjectStar.Key(id, bob))).isTrue();
        assertThat(stars.deleteByProjectAndUser(id, bob)).isEqualTo(1);
        assertThat(stars.deleteByProjectAndUser(id, bob)).isZero();
    }

    @Test
    @DisplayName("the aggregate persists a new project with version 1 and its file through cascades")
    void persistsAggregate() {
        Project project = Project.create(new Project.NewProject("fresh", "desc", Visibility.PRIVATE, "main", "main.js"),
                alice, "/record/template.js", T0);

        projects.saveAndFlush(project);

        assertThat(jdbc.queryForObject("select count(*) from version where project_id = ?", Integer.class, project.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select f.file_url from file f join version v on v.version_id = f.version_id where v.project_id = ?",
                String.class, project.getId())).isEqualTo("/record/template.js");
    }
}
