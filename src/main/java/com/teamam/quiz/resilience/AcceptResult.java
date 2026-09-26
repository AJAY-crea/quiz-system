package com.teamam.quiz.resilience;

/**
 * The ack sent back to the student. For DUPLICATE, sameSubmission tells the browser whether its
 * resend matched the stored answer ("saved") or was a different second answer ("first stands").
 */
public record AcceptResult(
        String submissionId, AcceptOutcome outcome, Boolean onTime, Boolean sameSubmission, String reason) {

    static AcceptResult accepted(String submissionId, boolean onTime) {
        return new AcceptResult(submissionId, AcceptOutcome.ACCEPTED, onTime, null, null);
    }

    static AcceptResult duplicate(String submissionId, boolean storedOnTime, boolean sameSubmission) {
        return new AcceptResult(submissionId, AcceptOutcome.DUPLICATE, storedOnTime, sameSubmission, null);
    }

    static AcceptResult rejected(String submissionId, String reason) {
        return new AcceptResult(submissionId, AcceptOutcome.REJECTED, null, null, reason);
    }
}
