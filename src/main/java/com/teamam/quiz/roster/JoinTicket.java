package com.teamam.quiz.roster;

/** What a phone gets after scanning: the ticket and how long it lasts. */
public record JoinTicket(String ticket, long expiresInMs) {
}
