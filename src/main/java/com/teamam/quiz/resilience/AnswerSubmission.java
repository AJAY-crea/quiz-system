package com.teamam.quiz.resilience;

import java.util.List;

/**
 * An answer as sent by the browser. chosenIndex (MCQ) and chosenIndexes (MULTI) are positions in
 * the student's own shuffled view; typedAnswer is used for typed questions. seq orders one student's
 * changes of mind on a choice question (a bigger seq is a later tap); it is never used as a time:
 * on-time is still judged only by the server's clock.
 */
public record AnswerSubmission(
        String submissionId,
        String rollNo,
        String deviceToken,
        Long questionId,
        Integer chosenIndex,
        String typedAnswer,
        List<Integer> chosenIndexes,
        Long seq) {

    /** An answer that can't replace an earlier one (no seq): typed answers, and older clients. */
    public AnswerSubmission(
            String submissionId, String rollNo, String deviceToken, Long questionId, Integer chosenIndex, String typedAnswer) {
        this(submissionId, rollNo, deviceToken, questionId, chosenIndex, typedAnswer, null, null);
    }
}
