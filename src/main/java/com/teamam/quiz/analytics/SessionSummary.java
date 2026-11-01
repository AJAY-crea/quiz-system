package com.teamam.quiz.analytics;

import java.time.Instant;

/** One lecture in the semester view. The averages are null if no question has answers yet. */
public record SessionSummary(
        Long sessionId, String title, Instant createdAt, int rosterSize, long joined, long released,
        Double averagePercentCorrect, Integer hardestQuestionOrder, Double hardestQuestionPercent) {
}
