package com.teamam.quiz.roster;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Rotating QR join codes and join tickets, as pure functions of (secret, session, time).
 *
 * A code is valid for its own 15 s slot and the one after it, so a forwarded photo of the QR goes
 * stale within ~30 s. Scanning exchanges the code for a ticket valid for 2 minutes, which is what
 * the join itself needs, so a slow typist isn't refused. Both are HMAC-SHA256 over a per-session
 * secret kept in the database: nothing is held in memory, so they survive an app restart.
 */
public final class JoinCodes {

    public static final Duration CODE_PERIOD = Duration.ofSeconds(15);
    public static final Duration TICKET_TTL = Duration.ofMinutes(2);

    private static final int CODE_HEX_CHARS = 8;
    private static final int TICKET_MAC_HEX_CHARS = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private JoinCodes() {
    }

    public static byte[] newSecret() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return secret;
    }

    public static String codeFor(byte[] secret, long sessionId, Instant at) {
        return codeForSlot(secret, sessionId, slotOf(at));
    }

    public static boolean isCodeValid(byte[] secret, long sessionId, String code, Instant now) {
        if (code == null) {
            return false;
        }
        long slot = slotOf(now);
        return constantTimeEquals(code, codeForSlot(secret, sessionId, slot))
                || constantTimeEquals(code, codeForSlot(secret, sessionId, slot - 1));
    }

    /** When the code shown right now stops being the current one (the next rotation). */
    public static Instant nextRotation(Instant now) {
        return Instant.ofEpochSecond((slotOf(now) + 1) * CODE_PERIOD.toSeconds());
    }

    public static String ticketFor(byte[] secret, long sessionId, Instant now) {
        long expiresAt = now.plus(TICKET_TTL).getEpochSecond();
        return expiresAt + "." + ticketMac(secret, sessionId, expiresAt);
    }

    public static boolean isTicketValid(byte[] secret, long sessionId, String ticket, Instant now) {
        if (ticket == null) {
            return false;
        }
        int dot = ticket.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(ticket.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        return now.getEpochSecond() <= expiresAt
                && constantTimeEquals(ticket.substring(dot + 1), ticketMac(secret, sessionId, expiresAt));
    }

    private static long slotOf(Instant at) {
        return at.getEpochSecond() / CODE_PERIOD.toSeconds();
    }

    private static String codeForSlot(byte[] secret, long sessionId, long slot) {
        return hmacHex(secret, "code:" + sessionId + ":" + slot).substring(0, CODE_HEX_CHARS).toUpperCase();
    }

    private static String ticketMac(byte[] secret, long sessionId, long expiresAt) {
        return hmacHex(secret, "ticket:" + sessionId + ":" + expiresAt).substring(0, TICKET_MAC_HEX_CHARS);
    }

    private static String hmacHex(byte[] secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Comparison time doesn't depend on how many leading characters match, so codes can't be guessed char by char. */
    private static boolean constantTimeEquals(String given, String expected) {
        return MessageDigest.isEqual(
                given.toUpperCase().getBytes(StandardCharsets.UTF_8),
                expected.toUpperCase().getBytes(StandardCharsets.UTF_8));
    }
}
