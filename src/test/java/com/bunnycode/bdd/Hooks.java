package com.bunnycode.bdd;

import com.bunnycode.support.TestData;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

public class Hooks {

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final World world;

    public Hooks(JdbcTemplate jdbc, StringRedisTemplate redis, World world) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.world = world;
    }

    @Before
    public void startFromACleanSlate() {
        new TestData(jdbc).clear();
        redis.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
    }

    @After
    public void disconnectEveryone() {
        world.allSessions().forEach(StompTestSession::close);
    }
}
