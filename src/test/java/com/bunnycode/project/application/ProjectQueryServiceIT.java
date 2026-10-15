package com.bunnycode.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bunnycode.MySqlTestConfiguration;
import com.bunnycode.identity.UserDirectory;
import com.bunnycode.identity.UserSummary;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.web.PageResponse;
import com.bunnycode.storage.FileStorage;
import com.bunnycode.support.QueryCounter;
import com.bunnycode.support.TestData;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MySqlTestConfiguration.class, ProjectQueryService.class, ProjectQueryServiceIT.Collaborators.class})
@DisplayName("ProjectQueryService — fixed number of queries per request")
class ProjectQueryServiceIT {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ProjectQueryService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private CountingDirectory directory;

    private TestData data;
    private long alice;

    @BeforeEach
    void seed() {
        data = new TestData(jdbc);
        data.clear();
        alice = data.user("alice");
        directory.calls.set(0);
    }

    @Test
    @DisplayName("Project detail costs the same 3 statements for 1 version or 30 versions (+1 batched owner lookup)")
    void detailHasNoNPlusOne() {
        project("small", 1);
        project("large", 30);
        QueryCounter counter = new QueryCounter(entityManagerFactory);

        long small = counter.count(() -> service.detail("small", Optional.empty()));
        long large = counter.count(() -> {
            ProjectDetailView detail = service.detail("large", Optional.empty());
            assertThat(detail.versions()).hasSize(30).allSatisfy(version -> {
                assertThat(version.file()).isNotNull();
                assertThat(version.recording()).isNotNull();
            });
        });

        assertThat(small).isEqualTo(3); // project, versions+files, recordings
        assertThat(large).isEqualTo(small);
        assertThat(directory.calls).hasValue(2); // one owner lookup per request, not per version
    }

    @Test
    @DisplayName("A feed page costs 2 statements (rows + count) and one batched owner lookup, whatever the page size")
    void feedHasNoNPlusOne() {
        for (int i = 0; i < 20; i++) {
            data.project("p" + i, data.user("owner" + i), true, T0.plusSeconds(i));
        }
        QueryCounter counter = new QueryCounter(entityManagerFactory);
        PageResponse<ProjectSummaryView>[] page = new PageResponse[1];

        long statements = counter.count(() -> page[0] = service.publicProjects(null, ProjectSort.RECENT, 0, 20));

        assertThat(statements).isEqualTo(2);
        assertThat(page[0].content()).hasSize(20).allSatisfy(summary -> assertThat(summary.owner()).isNotNull());
        assertThat(directory.calls).hasValue(1);
    }

    @Test
    @DisplayName("A private project is 'not found' for other users and anonymous visitors, but visible to its owner")
    void privateProjects() {
        long project = data.project("hidden", alice, false, T0);
        data.file(data.version(project, "main", 1), "main.js", "/record/x.js", false);

        assertThat(service.detail("hidden", Optional.of(new AuthenticatedUser(alice, "alice"))).name()).isEqualTo("hidden");
        assertThatThrownBy(() -> service.detail("hidden", Optional.empty()))
                .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("project.not-found"));
        assertThatThrownBy(() -> service.detail("hidden", Optional.of(new AuthenticatedUser(alice + 1000, "eve"))))
                .isInstanceOf(BunnyException.class);
    }

    private void project(String name, int versionCount) {
        long project = data.project(name, alice, true, T0);
        for (int number = 1; number <= versionCount; number++) {
            long version = data.version(project, "v" + number, number);
            data.file(version, "main.js", "/record/" + name + "-" + number + ".js", false);
            data.recording(version, T0, T0.plusSeconds(30));
        }
    }

    /** Stand-ins for the identity and storage modules; the directory counts its calls. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Collaborators {

        @Bean
        CountingDirectory userDirectory() {
            return new CountingDirectory();
        }

        @Bean
        FileStorage fileStorage() {
            return new FileStorage() {
                @Override
                public void store(String key, byte[] content, String contentType) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String publicUrl(String key) {
                    return "https://cdn.test" + key;
                }
            };
        }
    }

    static class CountingDirectory implements UserDirectory {

        final AtomicInteger calls = new AtomicInteger();

        @Override
        public Optional<UserSummary> findById(long userId) {
            calls.incrementAndGet();
            return Optional.of(summary(userId));
        }

        @Override
        public Map<Long, UserSummary> findAllByIds(Collection<Long> userIds) {
            calls.incrementAndGet();
            return userIds.stream().collect(Collectors.toMap(Function.identity(), CountingDirectory::summary));
        }

        @Override
        public Set<Long> findIdsByNameContaining(String fragment, int limit) {
            calls.incrementAndGet();
            return Set.of();
        }

        private static UserSummary summary(long id) {
            return new UserSummary(id, "user" + id, "https://cdn.test/user.png");
        }
    }
}
