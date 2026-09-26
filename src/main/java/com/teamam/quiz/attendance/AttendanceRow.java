package com.teamam.quiz.attendance;

import java.util.List;

/** flags are review signals for the instructor; they never change status. */
public record AttendanceRow(
        String rollNo, AttendanceStatus status, boolean joined, long answered, List<String> flags) {
}
