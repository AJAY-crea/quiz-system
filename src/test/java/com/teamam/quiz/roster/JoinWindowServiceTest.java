package com.teamam.quiz.roster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamam.quiz.MutableClock;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.QuizSessionRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Uses a movable clock (not the app's real one) so window expiry is tested without sleeping. */
@SpringBootTest
@Transactional
class JoinWindowServiceTest {

    @Autowired
    private QuizSessionRepository sessionRepository;

    private MutableClock clock;
    private JoinWindowService joinWindow;
    private Long sessionId;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-26T10:00:00Z"));
        joinWindow = new JoinWindowService(sessionRepository, clock);
        sessionId = sessionRepository.save(new QuizSession("join window test")).getId();
    }

    // Guards against: phones getting a ticket before the instructor has opened joining.
    @Test
    void refusesTicketWhileClosed() {
        assertThat(joinWindow.status(sessionId).open()).isFalse();
        assertThat(joinWindow.status(sessionId).code()).isNull();
        assertThatThrownBy(() -> joinWindow.issueTicket(sessionId, "anything"))
                .isInstanceOf(JoinRefusedException.class);
    }

    // Guards against: a still-valid code (from the previous slot) being accepted after the window ended.
    @Test
    void refusesTicketOnceTheWindowHasEnded() {
        joinWindow.open(sessionId, 60);
        clock.advance(Duration.ofSeconds(50));
        String freshCode = joinWindow.status(sessionId).code();

        clock.advance(Duration.ofSeconds(15));

        assertThat(joinWindow.status(sessionId).open()).isFalse();
        assertThatThrownBy(() -> joinWindow.issueTicket(sessionId, freshCode))
                .isInstanceOf(JoinRefusedException.class);
    }

    // Guards against: a student who scanned in time being refused while still typing their roll number.
    @Test
    void ticketIssuedWhileOpenStillWorksAfterClose() {
        joinWindow.open(sessionId, 60);
        String ticket = joinWindow.issueTicket(sessionId, joinWindow.status(sessionId).code()).ticket();

        joinWindow.close(sessionId);
        clock.advance(Duration.ofSeconds(90));

        assertThat(joinWindow.isValidTicket(sessionId, ticket)).isTrue();
        clock.advance(Duration.ofSeconds(60));
        assertThat(joinWindow.isValidTicket(sessionId, ticket)).isFalse();
    }

    // Guards against: "close" not actually stopping new scans, or "reopen" for latecomers not working.
    @Test
    void closeStopsNewTicketsAndReopenWorks() {
        joinWindow.open(sessionId, 60);
        String code = joinWindow.status(sessionId).code();
        joinWindow.close(sessionId);

        assertThatThrownBy(() -> joinWindow.issueTicket(sessionId, code))
                .isInstanceOf(JoinRefusedException.class);

        joinWindow.open(sessionId, 30);
        assertThat(joinWindow.issueTicket(sessionId, joinWindow.status(sessionId).code()).ticket()).isNotBlank();
    }
}
