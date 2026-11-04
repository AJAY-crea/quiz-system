package com.teamam.quiz.roster;

import java.time.Instant;

/** code is null while joining is closed, so the instructor screen shows no QR. */
public record JoinWindowStatus(
        boolean open, Instant openUntil, long secondsLeft, String code, long codeRotatesInMs, int codePeriodSeconds) {
}
