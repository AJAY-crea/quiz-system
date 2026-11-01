package com.teamam.quiz.session;

/**
 * Broadcast to every student on /topic/session/{id}/question. Deliberately carries no question
 * content: each browser fetches its own view (with its own option shuffle and a server-computed
 * remaining time) from GET /api/sessions/{id}/state.
 */
public record SessionEvent(String type, Long questionId) {

    public static final String RELEASED = "RELEASED";
    public static final String EXTENDED = "EXTENDED";
    public static final String MARKS = "MARKS";
    public static final String CLOSING = "CLOSING";
    public static final String ENDED = "ENDED";
}
