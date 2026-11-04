package com.teamam.quiz.session;

import java.util.List;

/**
 * What a (re)connecting student needs to resume. remainingMs is computed by the server so the
 * browser never has to trust or compare its own clock; currentQuestion is null before the first release.
 * myChoice is the stored choice in this student's own option order, so a reload shows their ticks;
 * myTypedAnswer is what they sent for a typed question.
 */
public record SessionStateView(
        Long sessionId, String status, StudentQuestionView currentQuestion, Long remainingMs, boolean alreadyAnswered,
        List<Integer> myChoice, String myTypedAnswer) {
}
