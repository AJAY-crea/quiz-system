package com.teamam.quiz.roster;

import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.QuizSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The instructor-controlled joining window. The window gates SCANNING (exchanging the QR code for
 * a ticket), not typing: a ticket issued while the window was open stays valid for its full
 * 2 minutes even if the window closes meanwhile.
 */
@Service
public class JoinWindowService {

    public static final int MIN_WINDOW_SECONDS = 10;
    public static final int MAX_WINDOW_SECONDS = 3600;

    private final QuizSessionRepository sessionRepository;
    private final Clock clock;

    public JoinWindowService(QuizSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    /** Instructor opens joining for N seconds (creates the session secret the first time). */
    @Transactional
    public JoinWindowStatus open(Long sessionId, int seconds) {
        if (seconds < MIN_WINDOW_SECONDS || seconds > MAX_WINDOW_SECONDS) {
            throw new IllegalArgumentException(
                    "joining window must be " + MIN_WINDOW_SECONDS + "-" + MAX_WINDOW_SECONDS + " seconds");
        }
        QuizSession session = requireSession(sessionId);
        if (session.getJoinSecret() == null) {
            session.setJoinSecret(Base64.getEncoder().encodeToString(JoinCodes.newSecret()));
        }
        session.setJoinOpenUntil(clock.instant().plusSeconds(seconds));
        sessionRepository.save(session);
        return statusOf(session);
    }

    /** Instructor closes joining now; existing students can still reconnect. */
    @Transactional
    public JoinWindowStatus close(Long sessionId) {
        QuizSession session = requireSession(sessionId);
        if (session.getJoinOpenUntil() != null && session.getJoinOpenUntil().isAfter(clock.instant())) {
            session.setJoinOpenUntil(clock.instant());
            sessionRepository.save(session);
        }
        return statusOf(session);
    }

    /** Is joining open, how long is left, and the current QR code (shown on the dashboard). */
    @Transactional(readOnly = true)
    public JoinWindowStatus status(Long sessionId) {
        return statusOf(requireSession(sessionId));
    }

    /** Called by a phone right after scanning: a fresh code while the window is open buys a ticket. */
    @Transactional(readOnly = true)
    public JoinTicket issueTicket(Long sessionId, String code) {
        QuizSession session = requireSession(sessionId);
        Instant now = clock.instant();
        if (!isOpen(session, now)) {
            throw new JoinRefusedException("Joining is closed. Ask the instructor to open it.");
        }
        if (!JoinCodes.isCodeValid(secretOf(session), sessionId, code, now)) {
            throw new JoinRefusedException("This QR code has expired. Scan the code on the screen again.");
        }
        return new JoinTicket(
                JoinCodes.ticketFor(secretOf(session), sessionId, now), JoinCodes.TICKET_TTL.toMillis());
    }

    /** Used at join time: is this ticket genuine and unexpired for this session? */
    @Transactional(readOnly = true)
    public boolean isValidTicket(Long sessionId, String ticket) {
        QuizSession session = requireSession(sessionId);
        return session.getJoinSecret() != null
                && JoinCodes.isTicketValid(secretOf(session), sessionId, ticket, clock.instant());
    }

    /** Builds the status for the dashboard (no code is shown while closed). */
    private JoinWindowStatus statusOf(QuizSession session) {
        Instant now = clock.instant();
        if (!isOpen(session, now)) {
            return new JoinWindowStatus(false, session.getJoinOpenUntil(), 0, null, 0);
        }
        return new JoinWindowStatus(
                true,
                session.getJoinOpenUntil(),
                Duration.between(now, session.getJoinOpenUntil()).toSeconds(),
                JoinCodes.codeFor(secretOf(session), session.getId(), now),
                Duration.between(now, JoinCodes.nextRotation(now)).toMillis());
    }

    /** Joining is open if the end time is set and still in the future. */
    private static boolean isOpen(QuizSession session, Instant now) {
        return session.getJoinOpenUntil() != null && now.isBefore(session.getJoinOpenUntil());
    }

    /** Reads the session's stored secret. */
    private static byte[] secretOf(QuizSession session) {
        return Base64.getDecoder().decode(session.getJoinSecret());
    }

    /** Loads the session or fails with "not found". */
    private QuizSession requireSession(Long sessionId) {
        return sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
    }
}
