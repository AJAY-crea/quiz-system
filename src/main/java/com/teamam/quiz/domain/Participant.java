package com.teamam.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Roster-verified join record binding a roll number to the device token it joined with,
 * scoped to one session. Not one of the design doc's six headline entities, but required
 * to implement Roster's documented same-device / same-roll-number mismatch flagging.
 */
@Entity
@Table(name = "participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "roll_no"}))
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private QuizSession session;

    @Column(name = "roll_no")
    private String rollNo;

    private String deviceToken;

    private Instant joinedAt = Instant.now();

    private boolean flagged = false;

    private String flagReason;

    protected Participant() {
    }

    public Participant(QuizSession session, String rollNo, String deviceToken) {
        this.session = session;
        this.rollNo = rollNo;
        this.deviceToken = deviceToken;
    }

    public Long getId() {
        return id;
    }

    public QuizSession getSession() {
        return session;
    }

    public String getRollNo() {
        return rollNo;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public String getFlagReason() {
        return flagReason;
    }

    public void flag(String reason) {
        this.flagged = true;
        this.flagReason = reason;
    }
}
