package com.teamam.quiz.session;

import com.teamam.quiz.domain.Question;
import java.util.List;

/** A question as the instructor sees it (no answer key is included). */
public record QuestionView(Long id, int orderIndex, String type, String text, List<String> options, int durationSeconds) {

    /** Copies a stored question into this view. */
    public static QuestionView of(Question question) {
        return new QuestionView(
                question.getId(),
                question.getOrderIndex(),
                question.getType().name(),
                question.getText(),
                List.copyOf(question.getOptions()),
                question.getDurationSeconds());
    }
}
