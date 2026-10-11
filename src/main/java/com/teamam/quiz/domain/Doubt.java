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
import java.time.Instant;

/** Table: an anonymous doubt a student sent, tagged with the question open at the time. */
@Entity
@Table(name = "doubts")
public class Doubt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private QuizSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @Column(name = "author_roll_no")
    private String authorRollNo;

    @Column(length = 1000)
    private String text;

    private int upvotes = 0;

    private boolean dismissed = false;

    private Instant createdAt = Instant.now();

    protected Doubt() {
    }

    public Doubt(QuizSession session, Question question, String authorRollNo, String text) {
        this.session = session;
        this.question = question;
        this.authorRollNo = authorRollNo;
        this.text = text;
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

    public String getAuthorRollNo() {
        return authorRollNo;
    }

    public String getText() {
        return text;
    }

    public int getUpvotes() {
        return upvotes;
    }

    public void setUpvotes(int upvotes) {
        this.upvotes = upvotes;
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void setDismissed(boolean dismissed) {
        this.dismissed = dismissed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
