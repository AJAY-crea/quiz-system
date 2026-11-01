package com.teamam.quiz.scoring;

import java.util.List;

/** What one student sees of their own marks, once published. No answer key and nobody else's marks. */
public record MyMarks(boolean published, List<Row> rows, double total, int outOf) {

    /** status: MARKED, AWAITING_MARKS (typed, teacher hasn't marked it yet), LATE or NOT_ANSWERED. */
    public record Row(int orderIndex, Double marks, int maxMarks, String status) {
    }

    /** The instructor hasn't published marks. */
    static MyMarks notPublished() {
        return new MyMarks(false, List.of(), 0, 0);
    }
}
