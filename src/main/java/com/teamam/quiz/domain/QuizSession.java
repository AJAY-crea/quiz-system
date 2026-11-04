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

/** Table: one lecture's quiz: its roster, which question is open, joining window, doubt settings. */
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

    /** Base64 HMAC key for this session's rotating join codes and tickets; created when joining first opens. */
    @Column(length = 64)
    private String joinSecret;

    /** New joins are allowed until this instant; null means joining has never been opened. */
    private Instant joinOpenUntil;

    // Nullable (null = default) so ddl-auto=update can add them to a database that already has sessions.
    private Boolean doubtsEnabled;
    private Boolean doubtAuthorsVisible;
    private Boolean marksVisibleToStudents;
    private Boolean doubtsShared;
    private Boolean integrityEnabled;
    private Integer codePeriodSeconds;

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

    public String getJoinSecret() {
        return joinSecret;
    }

    public void setJoinSecret(String joinSecret) {
        this.joinSecret = joinSecret;
    }

    public Instant getJoinOpenUntil() {
        return joinOpenUntil;
    }

    public void setJoinOpenUntil(Instant joinOpenUntil) {
        this.joinOpenUntil = joinOpenUntil;
    }

    /** On by default; the instructor can switch the doubts channel off (design doc risk #3). */
    public boolean isDoubtsEnabled() {
        return doubtsEnabled == null || doubtsEnabled;
    }

    public void setDoubtsEnabled(boolean enabled) {
        this.doubtsEnabled = enabled;
    }

    /** Off by default: the instructor sees doubts anonymously unless she chooses otherwise. */
    public boolean isDoubtAuthorsVisible() {
        return doubtAuthorsVisible != null && doubtAuthorsVisible;
    }

    public void setDoubtAuthorsVisible(boolean visible) {
        this.doubtAuthorsVisible = visible;
    }

    /** How often the join QR changes; null = the default 15 s. */
    public int getCodePeriodSeconds() {
        return codePeriodSeconds == null ? 15 : codePeriodSeconds;
    }

    public void setCodePeriodSeconds(int seconds) {
        this.codePeriodSeconds = seconds;
    }

    /** On by default; the instructor can stop recording integrity signals (design doc risk #3). */
    public boolean isIntegrityEnabled() {
        return integrityEnabled == null || integrityEnabled;
    }

    public void setIntegrityEnabled(boolean enabled) {
        this.integrityEnabled = enabled;
    }

    /** On by default: students see open doubts (never who asked) and can upvote them. She can switch this off. */
    public boolean isDoubtsShared() {
        return doubtsShared == null || doubtsShared;
    }

    public void setDoubtsShared(boolean shared) {
        this.doubtsShared = shared;
    }

    /** Off by default: students see their own marks only once the instructor publishes them. */
    public boolean isMarksVisibleToStudents() {
        return marksVisibleToStudents != null && marksVisibleToStudents;
    }

    public void setMarksVisibleToStudents(boolean visible) {
        this.marksVisibleToStudents = visible;
    }
}
