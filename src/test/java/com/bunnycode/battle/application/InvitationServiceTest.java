package com.bunnycode.battle.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.bunnycode.battle.application.InvitationStore.SaveOutcome;
import com.bunnycode.battle.domain.BattleRepository;
import com.bunnycode.battle.domain.QuestionLevel;
import com.bunnycode.battle.domain.QuestionRepository;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.error.ErrorKind;
import com.bunnycode.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

@DisplayName("Battle invitations")
class InvitationServiceTest {

    private final BattleRepository battles = mock(BattleRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final InvitationStore store = mock(InvitationStore.class);
    private final BattleNotifier notifier = mock(BattleNotifier.class);
    private final InvitationService service = new InvitationService(battles, questions, store, mock(BattleLiveStore.class),
            notifier, new BattleProperties(Duration.ofSeconds(10), 3, 3, Duration.ofHours(1), 4),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), mock(PlatformTransactionManager.class));
    private final AuthenticatedUser alice = new AuthenticatedUser(1L, "alice");

    @Test
    @DisplayName("Given a free name and a question for the level, when inviting, then everyone online is told")
    void broadcasts() {
        given(questions.findIdsByLevel(QuestionLevel.EASY)).willReturn(List.of(1L));
        given(store.save(any(), any())).willReturn(SaveOutcome.SAVED);

        var invitation = service.invite(alice, "duel", QuestionLevel.EASY);

        assertThat(invitation.expiresAt()).isEqualTo(Instant.EPOCH.plusSeconds(10));
        then(notifier).should().broadcastInvitation(invitation);
    }

    @Test
    @DisplayName("Given I invited moments ago, when inviting again, then I am asked to wait")
    void throttled() {
        given(questions.findIdsByLevel(QuestionLevel.EASY)).willReturn(List.of(1L));
        given(store.save(any(), any())).willReturn(SaveOutcome.INVITER_BUSY);

        assertThatThrownBy(() -> service.invite(alice, "duel", QuestionLevel.EASY))
                .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.kind()).isEqualTo(ErrorKind.TOO_MANY_REQUESTS));
        then(notifier).should(never()).broadcastInvitation(any());
    }

    @Test
    @DisplayName("Given the battle name exists, when inviting, then it conflicts")
    void nameTaken() {
        given(battles.existsByName("duel")).willReturn(true);
        assertThatThrownBy(() -> service.invite(alice, "duel", QuestionLevel.EASY))
                .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("battle.name-taken"));
    }

    @Test
    @DisplayName("Given my own invitation, when I accept it, then it is refused and left for others")
    void selfAccept() {
        given(store.peek("inv")).willReturn(Optional.of(new Invitation("inv", "duel", QuestionLevel.EASY, 1L, "alice",
                Instant.EPOCH)));

        assertThatThrownBy(() -> service.accept(alice, "inv"))
                .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("battle.self-invite"));
        then(store).should(never()).claim(any());
    }

    @Test
    @DisplayName("Given the invitation expired or was taken, when accepting, then I am told so")
    void expired() {
        given(store.peek("inv")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept(new AuthenticatedUser(2L, "bob"), "inv"))
                .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("battle.invitation-expired"));
    }
}
