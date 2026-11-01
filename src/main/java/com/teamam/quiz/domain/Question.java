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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Table: one question in a session: text, options, answer key, time limit, when it was released. */
@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private QuizSession session;

    /** 1-based release order within the session. */
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    private QuestionType type;

    @Column(length = 2000)
    private String text;

    // EAGER: a question has only a few options, and they're needed whenever the question is shown,
    // including when a reply is written after the database session has closed.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_index")
    @Column(name = "option_text")
    private List<String> options = new ArrayList<>();

    private Integer correctOptionIndex;

    private int durationSeconds = 30;

    /** Set by SessionEngine.release(); null until this question has been released. */
    private Instant releasedAt;

    /** Extra seconds granted by the instructor after release (e.g. after an app restart). */
    private int extensionSeconds = 0;

    /** Nullable (null = 1 mark) so ddl-auto=update can add it to a database that already has questions. */
    private Integer maxMarks;

    protected Question() {
    }

    public Question(QuizSession session, int orderIndex, QuestionType type, String text) {
        this.session = session;
        this.orderIndex = orderIndex;
        this.type = type;
        this.text = text;
    }

    public Long getId() {
        return id;
    }

    public QuizSession getSession() {
        return session;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public QuestionType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        // Copy: Hibernate must be able to modify this collection, and callers may pass List.of(...).
        this.options = new ArrayList<>(options);
    }

    public Integer getCorrectOptionIndex() {
        return correctOptionIndex;
    }

    public void setCorrectOptionIndex(Integer correctOptionIndex) {
        this.correctOptionIndex = correctOptionIndex;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public int getExtensionSeconds() {
        return extensionSeconds;
    }

    public void setExtensionSeconds(int extensionSeconds) {
        this.extensionSeconds = extensionSeconds;
    }

    public int getMaxMarks() {
        return maxMarks == null ? 1 : maxMarks;
    }

    public void setMaxMarks(int maxMarks) {
        this.maxMarks = maxMarks;
    }
}
