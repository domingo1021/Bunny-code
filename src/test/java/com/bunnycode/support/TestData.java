package com.bunnycode.support;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/**
 * Inserts rows with plain SQL, independent of the code under test, so a broken mapping cannot
 * hide itself by also writing the fixture.
 */
public final class TestData {

    private final JdbcTemplate jdbc;

    public TestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long user(String name) {
        return insert("insert into `user` (user_name, email, password) values (?, ?, ?)",
                name, name + "@example.com", "{bcrypt}$2a$04$placeholderplaceholderplaceholderplaceholderplac");
    }

    public long project(String name, long ownerId, boolean isPublic, Instant createdAt) {
        return insert("insert into project (project_name, project_description, is_public, user_id, create_at) values (?, ?, ?, ?, ?)",
                name, "about " + name, isPublic, ownerId, java.sql.Timestamp.from(createdAt));
    }

    public long version(long projectId, String name, int number) {
        return insert("insert into version (version_name, version_number, project_id) values (?, ?, ?)", name, number, projectId);
    }

    public long file(long versionId, String name, String key, boolean superseded) {
        return insert("insert into file (file_name, file_url, log, version_id, hided) values (?, ?, ?, ?, ?)",
                name, key, "1700000000000", versionId, superseded);
    }

    public long recording(long versionId, Instant start, Instant end) {
        return insert("insert into record (start_time, end_time, version_id, base_url) values (?, ?, ?, ?)",
                java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), versionId, "/record/base.js");
    }

    public long questionId(String name) {
        return jdbc.queryForObject("select question_id from question where question_name = ?", Long.class, name);
    }

    public long battle(String name, long first, long second, long questionId, String status, int watchCount) {
        return insert("insert into battle (battle_name, status, first_user_id, second_user_id, question_id, watch_count) values (?, ?, ?, ?, ?, ?)",
                name, status, first, second, questionId, watchCount);
    }

    /** Clears mutable data; keeps Flyway-seeded reference data (questions, test cases). */
    public void clear() {
        for (String table : new String[] {"project_star", "record", "file", "version", "project", "battle", "host", "`user`"}) {
            jdbc.update("delete from " + table);
        }
    }

    private long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
}
