package com.bunnycode.battle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Battle")
class BattleTest {

    @Test
    @DisplayName("A new battle waits for both players and has no winner code yet")
    void startsWaiting() {
        Battle battle = Battle.between("duel", 1L, 2L, mock(Question.class), Instant.EPOCH);

        assertThat(battle.getStatus()).isEqualTo(BattleStatus.WAITING);
        assertThat(battle.getWinnerCodeKey()).isEmpty();
        assertThat(battle.playerIds()).containsExactly(1L, 2L);
        assertThat(battle.isPlayer(1L)).isTrue();
        assertThat(battle.isPlayer(3L)).isFalse();
    }

    @Test
    @DisplayName("Nobody can battle themselves")
    void distinctPlayers() {
        assertThatThrownBy(() -> Battle.between("duel", 1L, 1L, mock(Question.class), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = BattleStatus.class, names = {"FINISHED", "TERMINATED"})
    void overStatuses(BattleStatus status) {
        assertThat(status.isOver()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = QuestionLevel.class)
    @DisplayName("Question levels round-trip through their v1 numeric codes")
    void levelCodes(QuestionLevel level) {
        assertThat(QuestionLevel.fromCode(level.code())).isEqualTo(level);
    }
}
