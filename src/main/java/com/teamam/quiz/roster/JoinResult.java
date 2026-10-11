package com.teamam.quiz.roster;

/** The answer to a join attempt: accepted, accepted-but-flagged, or rejected (and why). */
public record JoinResult(JoinOutcome outcome, String rollNo, String deviceToken, String flagReason) {

    /** Joined, nothing suspicious. */
    public static JoinResult accepted(String rollNo, String deviceToken) {
        return new JoinResult(JoinOutcome.ACCEPTED, rollNo, deviceToken, null);
    }

    /** Joined, but flagged for the instructor to review (never blocked). */
    public static JoinResult acceptedFlagged(String rollNo, String deviceToken, String flagReason) {
        return new JoinResult(JoinOutcome.ACCEPTED_FLAGGED, rollNo, deviceToken, flagReason);
    }

    /** Refused: roll number not on the roster. */
    public static JoinResult rejectedUnlisted(String rollNo) {
        return new JoinResult(JoinOutcome.REJECTED_UNLISTED, rollNo, null, null);
    }

    /** Refused: no valid ticket (QR not scanned, or scanned too long ago). */
    public static JoinResult rejectedInvalidTicket(String rollNo) {
        return new JoinResult(JoinOutcome.REJECTED_INVALID_TICKET, rollNo, null, null);
    }
}
