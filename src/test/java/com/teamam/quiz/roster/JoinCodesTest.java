package com.teamam.quiz.roster;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Plain unit tests: the rotating code and ticket rules are pure functions of (secret, session, time). */
class JoinCodesTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(UTF_8);
    /** 10:00:00 is a multiple of 15 s since the epoch, so it starts a code slot. */
    private static final Instant SLOT_START = Instant.parse("2026-09-26T10:00:00Z");

    // Guards against: a forwarded QR photo staying usable, or a just-scanned code being refused at rotation.
    @Test
    void codeIsAcceptedForCurrentAndPreviousSlotOnly() {
        String code = JoinCodes.codeFor(SECRET, 1L, SLOT_START.plusSeconds(3));

        assertThat(JoinCodes.isCodeValid(SECRET, 1L, code, SLOT_START.plusSeconds(14))).isTrue();
        assertThat(JoinCodes.isCodeValid(SECRET, 1L, code, SLOT_START.plusSeconds(20))).isTrue();
        assertThat(JoinCodes.isCodeValid(SECRET, 1L, code, SLOT_START.plusSeconds(30))).isFalse();
        assertThat(JoinCodes.codeFor(SECRET, 1L, SLOT_START.plusSeconds(16))).isNotEqualTo(code);
    }

    // Guards against: the code shown for one session being usable to join another.
    @Test
    void codeFromAnotherSessionIsRejected() {
        String codeForSession1 = JoinCodes.codeFor(SECRET, 1L, SLOT_START);

        assertThat(JoinCodes.isCodeValid(SECRET, 2L, codeForSession1, SLOT_START)).isFalse();
        assertThat(JoinCodes.isCodeValid(SECRET, 1L, null, SLOT_START)).isFalse();
    }

    // Guards against: a ticket living forever, being forged by editing its expiry, or crossing sessions.
    @Test
    void ticketExpiresAndCannotBeTampered() {
        String ticket = JoinCodes.ticketFor(SECRET, 1L, SLOT_START);

        assertThat(JoinCodes.isTicketValid(SECRET, 1L, ticket, SLOT_START.plusSeconds(119))).isTrue();
        assertThat(JoinCodes.isTicketValid(SECRET, 1L, ticket, SLOT_START.plusSeconds(121))).isFalse();
        assertThat(JoinCodes.isTicketValid(SECRET, 2L, ticket, SLOT_START)).isFalse();

        String[] parts = ticket.split("\\.", 2);
        String extendedExpiry = (Long.parseLong(parts[0]) + 3600) + "." + parts[1];
        assertThat(JoinCodes.isTicketValid(SECRET, 1L, extendedExpiry, SLOT_START.plusSeconds(200))).isFalse();

        assertThat(JoinCodes.isTicketValid(SECRET, 1L, "garbage", SLOT_START)).isFalse();
        assertThat(JoinCodes.isTicketValid(SECRET, 1L, null, SLOT_START)).isFalse();
    }
}
