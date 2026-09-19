package com.teamam.quiz.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "quiz_sessions")
public class QuizSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.CREATED;

    private Instant createdAt = Instant.now();

    /** Order index of the last question released; 0 means none released yet. */
    private int currentQuestionOrder = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_roster", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "roll_no")
    private Set<String> rosterRollNumbers = new HashSet<>();

    protected QuizSession() {
    }

    public QuizSession(String title) {
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getCurrentQuestionOrder() {
        return currentQuestionOrder;
    }

    public void setCurrentQuestionOrder(int currentQuestionOrder) {
        this.currentQuestionOrder = currentQuestionOrder;
    }

    public Set<String> getRosterRollNumbers() {
        return rosterRollNumbers;
    }
}
