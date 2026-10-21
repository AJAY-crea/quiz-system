package com.teamam.quiz.integrity;

/** One dashboard row: student, question, signal type, how many times, and total seconds away. */
public record IntegritySummaryRow(String rollNo, int questionOrder, String eventType, long count, long awaySeconds) {
}
