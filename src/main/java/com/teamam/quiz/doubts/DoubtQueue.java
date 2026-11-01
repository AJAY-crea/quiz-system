package com.teamam.quiz.doubts;

import java.util.List;

/**
 * What a student sees of the class's doubts. There is deliberately no author field: authors can't
 * reach another phone because the data isn't in the reply. "mine" only ever tells a student about their own.
 */
public record DoubtQueue(boolean shared, List<Item> doubts) {

    /** One open doubt in the queue. */
    public record Item(Long id, Integer questionOrder, String text, int upvotes, boolean upvotedByMe, boolean mine) {
    }
}
