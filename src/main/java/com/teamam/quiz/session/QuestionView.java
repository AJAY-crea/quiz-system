package com.teamam.quiz.session;

import com.teamam.quiz.domain.Question;
import java.util.List;

public record QuestionView(Long id, int orderIndex, String type, String text, List<String> options, int durationSeconds) {

    public static QuestionView of(Question question) {
        return new QuestionView(
                question.getId(),
                question.getOrderIndex(),
                question.getType().name(),
                question.getText(),
                question.getOptions(),
                question.getDurationSeconds());
    }
}
