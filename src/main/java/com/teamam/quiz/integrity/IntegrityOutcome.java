package com.teamam.quiz.integrity;

/** What happened to a reported signal. */
public enum IntegrityOutcome {
    RECORDED,
    /** Not stored: wrong device, unknown/unreleased question, or the question has already closed. */
    REJECTED,
    /** Not stored: this student already has the maximum number of signals for this question. */
    CAPPED
}
