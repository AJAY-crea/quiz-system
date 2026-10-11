package com.teamam.quiz.roster;

/** The four possible results of a join attempt. */
public enum JoinOutcome {
    ACCEPTED,
    ACCEPTED_FLAGGED,
    REJECTED_UNLISTED,
    REJECTED_INVALID_TICKET
}
