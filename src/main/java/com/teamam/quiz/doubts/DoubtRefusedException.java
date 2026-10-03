package com.teamam.quiz.doubts;

/** The doubt can't be accepted right now; the message is shown to the student as-is. */
public class DoubtRefusedException extends RuntimeException {

    public DoubtRefusedException(String message) {
        super(message);
    }
}
