package com.teamam.quiz.roster;

public record JoinResult(JoinOutcome outcome, String rollNo, String deviceToken, String flagReason) {

    public static JoinResult accepted(String rollNo, String deviceToken) {
        return new JoinResult(JoinOutcome.ACCEPTED, rollNo, deviceToken, null);
    }

    public static JoinResult acceptedFlagged(String rollNo, String deviceToken, String flagReason) {
        return new JoinResult(JoinOutcome.ACCEPTED_FLAGGED, rollNo, deviceToken, flagReason);
    }

    public static JoinResult rejectedUnlisted(String rollNo) {
        return new JoinResult(JoinOutcome.REJECTED_UNLISTED, rollNo, null, null);
    }
}
