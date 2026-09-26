package com.teamam.quiz.roster;

/** ticket comes from scanning the QR (POST /api/sessions/{id}/join-ticket); not needed to rejoin from the same device. */
public record JoinRequest(String rollNo, String deviceToken, String ticket) {
}
