package com.teamam.quiz.session;

/** Thrown when a phone isn't the one that joined with that roll number (becomes 403). */
public class NotAParticipantException extends RuntimeException {

    public NotAParticipantException(String message) {
        super(message);
    }
}
