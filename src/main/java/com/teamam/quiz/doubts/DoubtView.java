package com.teamam.quiz.doubts;

import java.time.Instant;

/** Instructor's view of a doubt. authorRollNo is null unless she has turned author visibility on. */
public record DoubtView(
        Long id, Long questionId, Integer questionOrder, String text, String authorRollNo, boolean dismissed,
        Instant createdAt, int upvotes) {
}
