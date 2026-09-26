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

@Entity
@Table(name = "responses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "question_id", "roll_no"}))
public class Response {

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

    /** Generated once by the browser and reused on every resend of the same answer. */
    @Column(length = 64)
    private String submissionId;

    /** Canonical option index (already remapped from the student's shuffled view). */
    private Integer selectedOptionIndex;

    @Column(length = 4000)
    private String typedAnswer;

    /** Server clock at arrival; never a client-reported time. */
    private Instant submittedAt;

    private boolean onTime = true;

    /** MCQ correctness; null for typed answers awaiting instructor review. Late answers keep it but are excluded from scoring. */
    private Boolean correct;

    protected Response() {
    }

    public Response(QuizSession session, Question question, String rollNo) {
        this.session = session;
        this.question = question;
        this.rollNo = rollNo;
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

    public Integer getSelectedOptionIndex() {
        return selectedOptionIndex;
    }

    public void setSelectedOptionIndex(Integer selectedOptionIndex) {
        this.selectedOptionIndex = selectedOptionIndex;
    }

    public String getTypedAnswer() {
        return typedAnswer;
    }

    public void setTypedAnswer(String typedAnswer) {
        this.typedAnswer = typedAnswer;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public boolean isOnTime() {
        return onTime;
    }

    public void setOnTime(boolean onTime) {
        this.onTime = onTime;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public Boolean getCorrect() {
        return correct;
    }

    public void setCorrect(Boolean correct) {
        this.correct = correct;
    }
}
