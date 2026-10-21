package com.teamam.quiz.domain;

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

/** Table: one advisory signal (left page / exited fullscreen / pasted) for a student on a question. */
@Entity
@Table(name = "integrity_events")
public class IntegrityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private QuizSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "roll_no")
    private String rollNo;

    @Enumerated(EnumType.STRING)
    private IntegrityEventType eventType;

    private Instant occurredAt = Instant.now();

    /** How long the page was hidden, in ms (FOCUS_LOSS only; nullable so existing databases update cleanly). */
    @Column(name = "away_ms")
    private Long awayMs;

    protected IntegrityEvent() {
    }

    public IntegrityEvent(QuizSession session, Question question, String rollNo, IntegrityEventType eventType) {
        this.session = session;
        this.question = question;
        this.rollNo = rollNo;
        this.eventType = eventType;
    }

    /** A signal with how long the student was away (FOCUS_LOSS). */
    public IntegrityEvent(QuizSession session, Question question, String rollNo, IntegrityEventType eventType,
            Long awayMs) {
        this(session, question, rollNo, eventType);
        this.awayMs = awayMs;
    }

    public Long getId() {
        return id;
    }

    public QuizSession getSession() {
        return session;
    }

    public Question getQuestion() {
        return question;
    }

    public String getRollNo() {
        return rollNo;
    }

    public IntegrityEventType getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getAwayMs() {
        return awayMs;
    }
}
