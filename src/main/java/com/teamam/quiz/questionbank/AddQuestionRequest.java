package com.teamam.quiz.questionbank;

import com.teamam.quiz.domain.QuestionType;
import java.util.List;

/** What the dashboard sends to add a question. */
public record AddQuestionRequest(
        int orderIndex,
        QuestionType type,
        String text,
        List<String> options,
        Integer correctOptionIndex,
        int durationSeconds,
        Integer maxMarks) {
}
