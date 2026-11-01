package com.teamam.quiz.dashboard;

import com.teamam.quiz.roster.JoinWindowStatus;
import com.teamam.quiz.session.SessionView;
import java.util.List;

/** Everything the instructor's screen needs in one reply. */
public record DashboardView(
        SessionView session,
        int rosterSize,
        long joined,
        long flagged,
        List<QuestionRow> questions,
        JoinWindowStatus join) {

    /** remainingMs is set only for the question currently open (server-computed). */
    public record QuestionRow(
            Long id, int orderIndex, String type, String text, List<String> options, Integer correctOptionIndex,
            int durationSeconds, boolean released, long answered, Long remainingMs, int maxMarks, long awaitingMarks) {
    }
}
