package com.bunnycode.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.MySqlTestConfiguration;
import com.bunnycode.support.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestConfiguration.class)
@DisplayName("User repository (MySQL)")
class UserAccountRepositoryIT {

    @Autowired
    private UserAccountRepository users;
    @Autowired
    private JdbcTemplate jdbc;

    private TestData data;

    @BeforeEach
    void seed() {
        data = new TestData(jdbc);
        data.clear();
    }

    @Test
    @DisplayName("e-mail lookup is case-insensitive, so mixed-case v1 addresses still sign in")
    void caseInsensitiveEmail() {
        jdbc.update("insert into `user` (user_name, email, password) values ('legacy', 'Legacy@Example.com', ?)",
                "$2b$05$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234");

        assertThat(users.findByEmail("legacy@example.com")).get().extracting(UserAccount::getName).isEqualTo("legacy");
    }

    @Test
    @DisplayName("a new account gets the default picture and level from the mapping")
    void registers() {
        UserAccount saved = users.saveAndFlush(UserAccount.register("fresh", EmailAddress.of("fresh@example.com"), "{bcrypt}x"));

        assertThat(jdbc.queryForMap("select picture, level, follower_count from `user` where user_id = ?", saved.getId()))
                .containsEntry("picture", "/user/user_icon.png")
                .containsEntry("level", 1L)
                .containsEntry("follower_count", 0L);
    }

    @Test
    @DisplayName("summaries for many ids come back from one IN query")
    void summaries() {
        long a = data.user("alice");
        long b = data.user("bob");

        assertThat(users.findSummariesByIdIn(List.of(a, b, 999_999L)))
                .extracting(UserSummaryRow::name).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    @DisplayName("name search treats wildcards literally and respects the limit")
    void nameSearch() {
        data.user("under_score");
        data.user("underXscore");
        data.user("bunny1");
        data.user("bunny2");

        assertThat(users.findByNameContainingIgnoreCase("_", Limit.of(10))).hasSize(1);
        assertThat(users.findByNameContainingIgnoreCase("BUNNY", Limit.of(1))).hasSize(1);
    }
}
