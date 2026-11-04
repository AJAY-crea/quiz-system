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
        Integer maxMarks,
        List<Integer> correctOptionIndexes) {

    /** The key as a list: correctOptionIndexes for MULTI, or the single correctOptionIndex. */
    public List<Integer> key() {
        if (correctOptionIndexes != null) {
            return correctOptionIndexes;
        }
        return correctOptionIndex == null ? List.of() : List.of(correctOptionIndex);
    }
}
