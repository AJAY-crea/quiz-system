package com.teamam.quiz.domain;

/** Life of a session: CREATED until the first release, then RUNNING. */
public enum SessionStatus {
    CREATED,
    RUNNING,
    ENDED
}
