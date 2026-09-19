package com.teamam.quiz.session;

import com.teamam.quiz.domain.QuizSession;

public record SessionView(Long id, String title, String status, int currentQuestionOrder) {

    public static SessionView of(QuizSession session) {
        return new SessionView(
                session.getId(), session.getTitle(), session.getStatus().name(), session.getCurrentQuestionOrder());
    }
}
