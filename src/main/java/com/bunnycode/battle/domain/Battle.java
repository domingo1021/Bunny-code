package com.bunnycode.battle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A two-player race on one question. The entity is created once and read afterwards; its status,
 * winner and counters change only through the conditional updates in {@link BattleRepository}.
 * Players are referenced by id (they belong to the identity module).
 */
@Entity
@Table(name = "battle")
public class Battle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "battle_id", columnDefinition = "int unsigned")
    private Long id;

    @Column(name = "battle_name", nullable = false, unique = true, length = 30)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(16)")
    private BattleStatus status;

    @Column(name = "watch_count", nullable = false, insertable = false, updatable = false, columnDefinition = "int unsigned")
    private long watchCount;

    @Column(name = "first_user_id", nullable = false, updatable = false, columnDefinition = "int unsigned")
    private Long firstPlayerId;

    @Column(name = "second_user_id", nullable = false, updatable = false, columnDefinition = "int unsigned")
    private Long secondPlayerId;

    @Column(name = "winner_id", insertable = false, updatable = false, columnDefinition = "int unsigned")
    private Long winnerId;

    @Column(name = "winner_url", nullable = false, updatable = false)
    private String winnerCodeKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false, columnDefinition = "int unsigned")
    private Question question;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Battle() {
        // for JPA
    }

    private Battle(String name, long firstPlayerId, long secondPlayerId, Question question, Instant createdAt) {
        if (firstPlayerId == secondPlayerId) {
            throw new IllegalArgumentException("A battle needs two different players");
        }
        this.name = Objects.requireNonNull(name, "name");
        this.firstPlayerId = firstPlayerId;
        this.secondPlayerId = secondPlayerId;
        this.question = Objects.requireNonNull(question, "question");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = BattleStatus.WAITING;
        this.winnerCodeKey = "";
    }

    public static Battle between(String name, long inviterId, long accepterId, Question question, Instant now) {
        return new Battle(name, inviterId, accepterId, question, now);
    }

    public boolean isPlayer(long userId) {
        return firstPlayerId == userId || secondPlayerId == userId;
    }

    public List<Long> playerIds() {
        return List.of(firstPlayerId, secondPlayerId);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BattleStatus getStatus() {
        return status;
    }

    public long getWatchCount() {
        return watchCount;
    }

    public long getFirstPlayerId() {
        return firstPlayerId;
    }

    public long getSecondPlayerId() {
        return secondPlayerId;
    }

    public Long getWinnerId() {
        return winnerId;
    }

    /** Blank until a winner's code has been archived. */
    public String getWinnerCodeKey() {
        return winnerCodeKey;
    }

    public Question getQuestion() {
        return question;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Battle that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Battle.class.hashCode();
    }

    @Override
    public String toString() {
        return "Battle[id=" + id + ", name=" + name + ", status=" + status + "]";
    }
}
