package com.bunnycode.battle.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.RedisTestConfiguration;
import com.bunnycode.battle.application.Invitation;
import com.bunnycode.battle.application.InvitationStore.SaveOutcome;
import com.bunnycode.battle.application.PlayerState;
import com.bunnycode.battle.domain.QuestionLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

@DataRedisTest
@Import({RedisTestConfiguration.class, RedisBattleLiveStore.class, RedisInvitationStore.class,
        RedisBattleStoresIT.Json.class})
@DisplayName("Battle state in Redis")
class RedisBattleStoresIT {

    @Autowired
    private RedisBattleLiveStore live;
    @Autowired
    private RedisInvitationStore invitations;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void flush() {
        redis.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
    }

    @Nested
    @DisplayName("Live player state")
    class Live {

        @Test
        @DisplayName("opens with chances, records readiness and code, and expires on its own")
        void lifecycle() {
            live.open(9L, List.of(1L, 2L), 3, Duration.ofMinutes(10));
            live.markReady(9L, 1L);
            live.updateCode(9L, 2L, "let x = 1;");

            assertThat(live.players(9L, List.of(1L, 2L))).containsEntry(1L, new PlayerState(true, "", 3))
                    .containsEntry(2L, new PlayerState(false, "let x = 1;", 3));
            assertThat(redis.getExpire("battle:9:player:1")).isPositive();
        }

        @Test
        @DisplayName("chances run out at zero and can be refunded")
        void chances() {
            live.open(9L, List.of(1L), 2, Duration.ofMinutes(10));

            assertThat(live.consumeChance(9L, 1L)).isEqualTo(OptionalInt.of(1));
            assertThat(live.consumeChance(9L, 1L)).isEqualTo(OptionalInt.of(0));
            assertThat(live.consumeChance(9L, 1L)).isEmpty();
            live.refundChance(9L, 1L);
            assertThat(live.consumeChance(9L, 1L)).isEqualTo(OptionalInt.of(0));
        }

        @Test
        @DisplayName("after closing, late keystrokes do not resurrect state")
        void closed() {
            live.open(9L, List.of(1L), 3, Duration.ofMinutes(10));
            live.close(9L, List.of(1L));

            live.updateCode(9L, 1L, "late");
            live.markReady(9L, 1L);

            assertThat(live.isLive(9L, 1L)).isFalse();
            assertThat(live.players(9L, List.of(1L))).isEmpty();
            assertThat(live.consumeChance(9L, 1L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Invitations")
    class Invitations {

        private Invitation invitation(String id, long inviter, String name) {
            return new Invitation(id, name, QuestionLevel.EASY, inviter, "user" + inviter, Instant.parse("2026-10-14T10:00:00Z"));
        }

        @Test
        @DisplayName("are saved, peeked without consuming, and claimed exactly once")
        void claimOnce() {
            assertThat(invitations.save(invitation("a", 1L, "duel"), Duration.ofSeconds(10))).isEqualTo(SaveOutcome.SAVED);

            assertThat(invitations.peek("a")).contains(invitation("a", 1L, "duel"));
            assertThat(invitations.claim("a")).contains(invitation("a", 1L, "duel"));
            assertThat(invitations.claim("a")).isEmpty();
        }

        @Test
        @DisplayName("an inviter can have one pending invitation, and a pending name cannot be reused")
        void throttles() {
            invitations.save(invitation("a", 1L, "duel"), Duration.ofSeconds(10));

            assertThat(invitations.save(invitation("b", 1L, "other"), Duration.ofSeconds(10))).isEqualTo(SaveOutcome.INVITER_BUSY);
            assertThat(invitations.save(invitation("c", 2L, "duel"), Duration.ofSeconds(10))).isEqualTo(SaveOutcome.NAME_PENDING);
            assertThat(invitations.save(invitation("d", 2L, "fresh"), Duration.ofSeconds(10))).isEqualTo(SaveOutcome.SAVED);
        }

        @Test
        @DisplayName("expire with their TTL")
        void expire() {
            invitations.save(invitation("a", 1L, "duel"), Duration.ofMillis(200));

            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(invitations.peek("a")).isEmpty());
            assertThat(invitations.save(invitation("b", 1L, "duel"), Duration.ofSeconds(10))).isEqualTo(SaveOutcome.SAVED);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Json {
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }
}
