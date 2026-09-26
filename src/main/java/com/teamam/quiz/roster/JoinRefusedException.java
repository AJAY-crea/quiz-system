package com.teamam.quiz.roster;

/** The phone may not join right now; the message is shown to the student as-is. */
public class JoinRefusedException extends RuntimeException {

    public JoinRefusedException(String message) {
        super(message);
    }
}
