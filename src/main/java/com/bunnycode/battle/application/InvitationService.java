package com.bunnycode.battle.application;

import com.bunnycode.battle.application.BattleViews.InvitationView;
import com.bunnycode.battle.domain.Battle;
import com.bunnycode.battle.domain.BattleRepository;
import com.bunnycode.battle.domain.Question;
import com.bunnycode.battle.domain.QuestionLevel;
import com.bunnycode.battle.domain.QuestionRepository;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.security.AuthenticatedUser;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Open invitations: anyone online may accept, first come first served. An invitation lives a
 * few seconds and each inviter may have one at a time (v1's anti-spam rule).
 */
@Service
public class InvitationService {

    private final BattleRepository battles;
    private final QuestionRepository questions;
    private final InvitationStore invitations;
    private final BattleLiveStore liveStore;
    private final BattleNotifier notifier;
    private final BattleProperties properties;
    private final Clock clock;
    private final RandomGenerator random;
    private final TransactionTemplate transaction;

    InvitationService(BattleRepository battles, QuestionRepository questions, InvitationStore invitations,
                      BattleLiveStore liveStore, BattleNotifier notifier, BattleProperties properties, Clock clock,
                      PlatformTransactionManager transactionManager) {
        this.battles = battles;
        this.questions = questions;
        this.invitations = invitations;
        this.liveStore = liveStore;
        this.notifier = notifier;
        this.properties = properties;
        this.clock = clock;
        this.random = RandomGenerator.getDefault();
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public InvitationView invite(AuthenticatedUser inviter, String battleName, QuestionLevel level) {
        if (battles.existsByName(battleName)) {
            throw nameTaken();
        }
        if (questions.findIdsByLevel(level).isEmpty()) {
            throw BunnyException.conflict("battle.no-question", "There are no questions for that level yet.");
        }
        var invitation = new Invitation(UUID.randomUUID().toString(), battleName, level, inviter.id(), inviter.name(),
                clock.instant().plus(properties.invitationTimeToLive()));
        switch (invitations.save(invitation, properties.invitationTimeToLive())) {
            case INVITER_BUSY -> throw BunnyException.tooManyRequests("battle.invite-throttled",
                    "Please wait %d seconds before sending another invitation."
                            .formatted(properties.invitationTimeToLive().toSeconds()));
            case NAME_PENDING -> throw BunnyException.conflict("battle.name-pending",
                    "Someone is already inviting with that battle name.");
            case SAVED -> {
                // broadcast below
            }
        }
        var view = new InvitationView(invitation.id(), battleName, level, inviter.id(), inviter.name(),
                invitation.expiresAt());
        notifier.broadcastInvitation(view);
        return view;
    }

    /** Creates the battle and tells both players where to go. */
    public long accept(AuthenticatedUser accepter, String invitationId) {
        // Peek first so inviters clicking their own invitation do not consume it.
        Invitation pending = invitations.peek(invitationId).orElseThrow(InvitationService::expired);
        if (pending.inviterId() == accepter.id()) {
            throw BunnyException.invalid("battle.self-invite", "You can't accept your own invitation.");
        }
        Invitation invitation = invitations.claim(invitationId).orElseThrow(InvitationService::expired);

        long battleId = transaction.execute(status -> createBattle(invitation, accepter));
        List<Long> players = List.of(invitation.inviterId(), accepter.id());
        liveStore.open(battleId, players, properties.submissionChances(), properties.liveStateTimeToLive());
        players.forEach(player -> notifier.notifyBattleCreated(player, battleId));
        return battleId;
    }

    private long createBattle(Invitation invitation, AuthenticatedUser accepter) {
        List<Long> candidates = questions.findIdsByLevel(invitation.level());
        if (candidates.isEmpty()) {
            throw BunnyException.conflict("battle.no-question", "There are no questions for that level yet.");
        }
        Question question = questions.findById(candidates.get(random.nextInt(candidates.size()))).orElseThrow();
        Battle battle = Battle.between(invitation.battleName(), invitation.inviterId(), accepter.id(), question,
                clock.instant());
        try {
            return battles.saveAndFlush(battle).getId();
        } catch (DataIntegrityViolationException e) {
            throw nameTaken();
        }
    }

    private static BunnyException nameTaken() {
        return BunnyException.conflict("battle.name-taken", "That battle name is already taken.");
    }

    private static BunnyException expired() {
        return BunnyException.conflict("battle.invitation-expired",
                "This invitation has expired or someone else accepted it first.");
    }
}
