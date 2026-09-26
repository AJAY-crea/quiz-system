package com.teamam.quiz.attendance;

import java.util.List;

/**
 * basis says which rule decided status: PARTICIPATION (answered at least threshold x released
 * questions) or JOINED_ONLY (no question released yet, so joining is the only evidence).
 */
public record AttendanceReport(
        Long sessionId, int releasedQuestions, double threshold, String basis, List<AttendanceRow> rows) {

    public static final String BASIS_PARTICIPATION = "PARTICIPATION";
    public static final String BASIS_JOINED_ONLY = "JOINED_ONLY";
}
