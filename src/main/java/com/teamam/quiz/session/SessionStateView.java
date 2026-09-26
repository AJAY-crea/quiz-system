package com.teamam.quiz.session;

/**
 * What a (re)connecting student needs to resume. remainingMs is computed by the server so the
 * browser never has to trust or compare its own clock; currentQuestion is null before the first release.
 */
public record SessionStateView(
        Long sessionId, String status, StudentQuestionView currentQuestion, Long remainingMs, boolean alreadyAnswered) {
}
