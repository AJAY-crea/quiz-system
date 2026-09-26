package com.teamam.quiz.session;

import java.util.List;

/** A question as one student sees it: options in that student's shuffled order, no answer key. */
public record StudentQuestionView(
        Long id, int orderIndex, String type, String text, List<String> options, int durationSeconds) {
}
