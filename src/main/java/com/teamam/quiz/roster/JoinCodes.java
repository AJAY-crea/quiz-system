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
 * A code is valid for its own slot (15 s by default; the instructor can choose 5-60 s) and the one
 * after it, so a forwarded photo of the QR goes stale within two slots. Scanning exchanges the code for a ticket valid for 2 minutes, which is what
 * the join itself needs, so a slow typist isn't refused. Both are HMAC-SHA256 over a per-session
 * secret kept in the database: nothing is held in memory, so they survive an app restart.
 */
public final class JoinCodes {

    public static final Duration CODE_PERIOD = Duration.ofSeconds(15);
    public static final int MIN_PERIOD_SECONDS = 5;
    public static final int MAX_PERIOD_SECONDS = 60;
    public static final Duration TICKET_TTL = Duration.ofMinutes(2);

    private static final int CODE_HEX_CHARS = 8;
    private static final int TICKET_MAC_HEX_CHARS = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private JoinCodes() {
    }

    /** A fresh random secret for a session; the codes and tickets are computed from it. */
    public static byte[] newSecret() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return secret;
    }

    /** The 8-character QR code for this session at this moment, with the default 15 s rotation. */
    public static String codeFor(byte[] secret, long sessionId, Instant at) {
        return codeFor(secret, sessionId, at, CODE_PERIOD.toSeconds());
    }

    /** The 8-character QR code for this session at this moment (changes every periodSeconds). */
    public static String codeFor(byte[] secret, long sessionId, Instant at, long periodSeconds) {
        return codeForSlot(secret, sessionId, periodSeconds, at.getEpochSecond() / periodSeconds);
    }

    /** Is this scanned code from the current or previous 15-second slot? */
    public static boolean isCodeValid(byte[] secret, long sessionId, String code, Instant now) {
        return isCodeValid(secret, sessionId, code, now, CODE_PERIOD.toSeconds());
    }

    /** Is this scanned code from the current or previous slot? */
    public static boolean isCodeValid(byte[] secret, long sessionId, String code, Instant now, long periodSeconds) {
        if (code == null) {
            return false;
        }
        long slot = now.getEpochSecond() / periodSeconds;
        return constantTimeEquals(code, codeForSlot(secret, sessionId, periodSeconds, slot))
                || constantTimeEquals(code, codeForSlot(secret, sessionId, periodSeconds, slot - 1));
    }

    /** When the code shown right now stops being the current one (the next rotation). */
    public static Instant nextRotation(Instant now, long periodSeconds) {
        return Instant.ofEpochSecond((now.getEpochSecond() / periodSeconds + 1) * periodSeconds);
    }

    /** Makes a 2-minute join ticket: its expiry time + a signature only the server can make. */
    public static String ticketFor(byte[] secret, long sessionId, Instant now) {
        long expiresAt = now.plus(TICKET_TTL).getEpochSecond();
        return expiresAt + "." + ticketMac(secret, sessionId, expiresAt);
    }

    /** Is this ticket genuine (signature matches) and not expired? */
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

    /**
     * The code for one slot: a signature of (session, period, slot), shortened. The period is part of
     * it, so slot numbers from different speeds never produce the same code.
     */
    private static String codeForSlot(byte[] secret, long sessionId, long periodSeconds, long slot) {
        String message = periodSeconds == CODE_PERIOD.toSeconds()
                ? "code:" + sessionId + ":" + slot
                : "code:" + sessionId + ":" + periodSeconds + ":" + slot;
        return hmacHex(secret, message).substring(0, CODE_HEX_CHARS).toUpperCase();
    }

    /** The signature part of a ticket, so nobody can edit the expiry. */
    private static String ticketMac(byte[] secret, long sessionId, long expiresAt) {
        return hmacHex(secret, "ticket:" + sessionId + ":" + expiresAt).substring(0, TICKET_MAC_HEX_CHARS);
    }

    /** HMAC-SHA256: a keyed fingerprint; without the secret you can't produce a valid one. */
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
