package com.teamam.quiz.scoring;

import java.util.List;

/**
 * One typed question as the instructor marks it: identical answers collapsed into groups so each
 * wording is marked once. Late answers are listed apart; they score 0 and can't be marked.
 */
public record TypedReview(
        Long questionId, int orderIndex, String text, int maxMarks, long marked, long pending,
        List<Group> groups, List<Answer> late) {

    /** Answers that say the same thing. marks is their shared mark; null if unmarked or if they differ (mixed). */
    public record Group(String key, String sample, int count, Double marks, boolean mixed, List<Answer> answers) {
    }

    /** One student's answer, exactly as they typed it. */
    public record Answer(Long responseId, String rollNo, String text, Double marks) {
    }
}
