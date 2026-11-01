package com.teamam.quiz.analytics;

import java.util.List;

/**
 * How one question went. percentCorrect is over on-time answers (for typed: average of the marks
 * given, as a % of the maximum). discrimination is null when it can't be judged yet: too few
 * students, or typed answers still to mark. notes are plain warnings for the instructor.
 */
public record QuestionAnalytics(
        Long questionId, int orderIndex, String type, String text, int maxMarks,
        long answered, long late, long awaitingMarks,
        Double percentCorrect, Double discrimination,
        List<OptionStat> options, List<String> notes) {

    /** How many on-time answers chose this option (distractor pull, for the wrong options). */
    public record OptionStat(int optionIndex, String text, long count, double percent, boolean correct) {
    }
}
