package com.teamam.quiz.integrity;

import com.teamam.quiz.domain.IntegrityEventType;

/** Sent by the student page; no client timestamp: the server records its own arrival time. */
public record IntegrityReport(String rollNo, String deviceToken, Long questionId, IntegrityEventType eventType) {
}
