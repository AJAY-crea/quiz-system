package com.teamam.quiz.integrity;

/** One dashboard row: student, question, signal type, how many times. */
public record IntegritySummaryRow(String rollNo, int questionOrder, String eventType, long count) {
}
