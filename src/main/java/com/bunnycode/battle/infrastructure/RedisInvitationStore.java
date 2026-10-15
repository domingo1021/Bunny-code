package com.bunnycode.battle.infrastructure;

import com.bunnycode.battle.application.Invitation;
import com.bunnycode.battle.application.InvitationStore;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Three keys per invitation, all expiring together:
 * <pre>
 * battle:invitation:inviter:{userId}  one pending invitation per inviter (anti-spam)
 * battle:invitation:name:{battleName} the name is reserved while the invitation is open
 * battle:invitation:{id}              the invitation (JSON), consumed atomically with GETDEL
 * </pre>
 */
@Component
class RedisInvitationStore implements InvitationStore {

    private static final RedisScript<Long> SAVE = RedisScript.of("""
            if not redis.call('SET', KEYS[1], '1', 'NX', 'PX', ARGV[1]) then
              return 1
            end
            if not redis.call('SET', KEYS[2], '1', 'NX', 'PX', ARGV[1]) then
              redis.call('DEL', KEYS[1])
              return 2
            end
            redis.call('SET', KEYS[3], ARGV[2], 'PX', ARGV[1])
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    RedisInvitationStore(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public SaveOutcome save(Invitation invitation, Duration ttl) {
        Long outcome = redis.execute(SAVE,
                List.of("battle:invitation:inviter:" + invitation.inviterId(),
                        "battle:invitation:name:" + invitation.battleName(),
                        invitationKey(invitation.id())),
                String.valueOf(ttl.toMillis()), json.writeValueAsString(invitation));
        if (outcome == null) {
            throw new IllegalStateException("Redis returned no result for the invitation script");
        }
        return switch (outcome.intValue()) {
            case 0 -> SaveOutcome.SAVED;
            case 1 -> SaveOutcome.INVITER_BUSY;
            case 2 -> SaveOutcome.NAME_PENDING;
            default -> throw new IllegalStateException("Unexpected invitation script result: " + outcome);
        };
    }

    @Override
    public Optional<Invitation> peek(String invitationId) {
        return Optional.ofNullable(redis.opsForValue().get(invitationKey(invitationId))).map(this::parse);
    }

    @Override
    public Optional<Invitation> claim(String invitationId) {
        return Optional.ofNullable(redis.opsForValue().getAndDelete(invitationKey(invitationId))).map(this::parse);
    }

    private Invitation parse(String value) {
        return json.readValue(value, Invitation.class);
    }

    private static String invitationKey(String invitationId) {
        return "battle:invitation:" + invitationId;
    }
}
