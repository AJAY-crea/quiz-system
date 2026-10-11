package com.teamam.quiz.session;

import com.teamam.quiz.domain.QuizSession;

/** A short summary of a session for the dashboard. */
public record SessionView(Long id, String title, String status, int currentQuestionOrder) {

    /** Copies a stored session into this view. */
    public static SessionView of(QuizSession session) {
        return new SessionView(
                session.getId(), session.getTitle(), session.getStatus().name(), session.getCurrentQuestionOrder());
    }
}
