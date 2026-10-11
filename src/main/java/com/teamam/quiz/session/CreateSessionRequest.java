package com.teamam.quiz.session;

/** What the dashboard sends to create a session: just a title. */
public record CreateSessionRequest(String title) {
}
