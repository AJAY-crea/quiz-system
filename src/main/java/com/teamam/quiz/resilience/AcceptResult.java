package com.teamam.quiz.resilience;

/**
 * The ack sent back to the student. For DUPLICATE, sameSubmission tells the browser whether its
 * resend matched the stored answer ("saved") or was a different second answer ("first stands").
 */
public record AcceptResult(
        String submissionId, AcceptOutcome outcome, Boolean onTime, Boolean sameSubmission, String reason) {

    /** Saved for the first time (and whether it was on time). */
    static AcceptResult accepted(String submissionId, boolean onTime) {
        return new AcceptResult(submissionId, AcceptOutcome.ACCEPTED, onTime, null, null);
    }

    /** Already had an answer: either this same one resent, or a different second one. */
    static AcceptResult duplicate(String submissionId, boolean storedOnTime, boolean sameSubmission) {
        return new AcceptResult(submissionId, AcceptOutcome.DUPLICATE, storedOnTime, sameSubmission, null);
    }

    /** Not saved, with the reason shown to the student. */
    static AcceptResult rejected(String submissionId, String reason) {
        return new AcceptResult(submissionId, AcceptOutcome.REJECTED, null, null, reason);
    }
}
