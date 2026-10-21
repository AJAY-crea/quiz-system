package com.teamam.quiz.integrity;

import com.teamam.quiz.domain.IntegrityEventType;

/**
 * Sent by the student page. No client timestamp: the server records its own arrival time.
 * awayMs is how long the page was hidden (FOCUS_LOSS only), measured by the phone, so it is
 * advisory like the signal itself; null for PASTE / FULLSCREEN_EXIT.
 */
public record IntegrityReport(
        String rollNo, String deviceToken, Long questionId, IntegrityEventType eventType, Long awayMs) {

    /** A signal without a duration (paste, fullscreen exit). */
    public IntegrityReport(String rollNo, String deviceToken, Long questionId, IntegrityEventType eventType) {
        this(rollNo, deviceToken, questionId, eventType, null);
    }
}
