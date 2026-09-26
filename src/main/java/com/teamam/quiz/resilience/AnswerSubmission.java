package com.teamam.quiz.resilience;

/**
 * An answer as sent by the browser. chosenIndex is the position in the student's own shuffled
 * view (MCQ); typedAnswer is used for typed questions. No client timestamp is accepted.
 */
public record AnswerSubmission(
        String submissionId,
        String rollNo,
        String deviceToken,
        Long questionId,
        Integer chosenIndex,
        String typedAnswer) {
}
