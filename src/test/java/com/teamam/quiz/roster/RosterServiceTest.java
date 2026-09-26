package com.teamam.quiz.roster;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.QuizSessionRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class RosterServiceTest {

    @Autowired
    private RosterService rosterService;

    @Autowired
    private JoinWindowService joinWindow;

    @Autowired
    private QuizSessionRepository sessionRepository;

    // Guards against: unlisted roll numbers getting in, or a device switch going unnoticed.
    @Test
    void rejectsUnlistedAndFlagsDeviceMismatch() {
        QuizSession session = sessionRepository.save(new QuizSession("Week 1 roster test session"));
        List<String> roster =
                IntStream.rangeClosed(1, 30).mapToObj(i -> String.format("CS23B%03d", i)).toList();
        rosterService.uploadRoster(session.getId(), roster);
        String ticket = ticket(session.getId());

        String firstRollNo = roster.get(0);

        JoinResult listedJoin = rosterService.join(session.getId(), firstRollNo, "device-A", ticket);
        assertThat(listedJoin.outcome()).isEqualTo(JoinOutcome.ACCEPTED);

        JoinResult unlistedJoin = rosterService.join(session.getId(), "CS23B999", "device-B", ticket);
        assertThat(unlistedJoin.outcome()).isEqualTo(JoinOutcome.REJECTED_UNLISTED);

        JoinResult deviceMismatchJoin = rosterService.join(session.getId(), firstRollNo, "device-C", ticket);
        assertThat(deviceMismatchJoin.outcome()).isEqualTo(JoinOutcome.ACCEPTED_FLAGGED);
        assertThat(deviceMismatchJoin.flagReason()).isNotBlank();

        String secondRollNo = roster.get(1);
        JoinResult tokenReuseJoin = rosterService.join(session.getId(), secondRollNo, "device-C", ticket);
        assertThat(tokenReuseJoin.outcome()).isEqualTo(JoinOutcome.ACCEPTED_FLAGGED);
        assertThat(tokenReuseJoin.flagReason()).isNotBlank();
    }

    // Guards against: joining without having scanned the live QR (e.g. from a forwarded link).
    @Test
    void refusesNewJoinWithoutValidTicket() {
        QuizSession session = sessionRepository.save(new QuizSession("ticket test"));
        rosterService.uploadRoster(session.getId(), List.of("CS23B501"));
        joinWindow.open(session.getId(), 60);

        assertThat(rosterService.join(session.getId(), "CS23B501", "dev-1", null).outcome())
                .isEqualTo(JoinOutcome.REJECTED_INVALID_TICKET);
        assertThat(rosterService.join(session.getId(), "CS23B501", "dev-1", "123.forged").outcome())
                .isEqualTo(JoinOutcome.REJECTED_INVALID_TICKET);
    }

    // Guards against: a student who already joined being locked out by a reload after the window closes,
    // while a NEW device for that roll number is still refused.
    @Test
    void allowsSameDeviceRejoinAfterWindowCloses() {
        QuizSession session = sessionRepository.save(new QuizSession("rejoin test"));
        rosterService.uploadRoster(session.getId(), List.of("CS23B502"));
        rosterService.join(session.getId(), "CS23B502", "dev-own", ticket(session.getId()));
        joinWindow.close(session.getId());

        assertThat(rosterService.join(session.getId(), "CS23B502", "dev-own", null).outcome())
                .isEqualTo(JoinOutcome.ACCEPTED);
        assertThat(rosterService.join(session.getId(), "CS23B502", "dev-new", null).outcome())
                .isEqualTo(JoinOutcome.REJECTED_INVALID_TICKET);
    }

    // Guards against: one phone answering for different roll numbers in different lectures (proxy),
    // while still letting the join through for the instructor to judge.
    @Test
    void flagsDeviceUsedByDifferentRollNoInEarlierSession() {
        QuizSession lastWeek = sessionRepository.save(new QuizSession("last week"));
        rosterService.uploadRoster(lastWeek.getId(), List.of("CS23B503", "CS23B504"));
        rosterService.join(lastWeek.getId(), "CS23B503", "dev-shared", ticket(lastWeek.getId()));

        QuizSession today = sessionRepository.save(new QuizSession("today"));
        rosterService.uploadRoster(today.getId(), List.of("CS23B503", "CS23B504"));
        String ticket = ticket(today.getId());

        JoinResult proxy = rosterService.join(today.getId(), "CS23B504", "dev-shared", ticket);
        assertThat(proxy.outcome()).isEqualTo(JoinOutcome.ACCEPTED_FLAGGED);
        assertThat(proxy.flagReason()).contains("CS23B503");

        QuizSession nextWeek = sessionRepository.save(new QuizSession("next week"));
        rosterService.uploadRoster(nextWeek.getId(), List.of("CS23B505"));
        JoinResult sameStudentNewPhone =
                rosterService.join(nextWeek.getId(), "CS23B505", "dev-brand-new", ticket(nextWeek.getId()));
        assertThat(sameStudentNewPhone.outcome()).isEqualTo(JoinOutcome.ACCEPTED);
    }

    private String ticket(Long sessionId) {
        joinWindow.open(sessionId, 60);
        return joinWindow.issueTicket(sessionId, joinWindow.status(sessionId).code()).ticket();
    }
}
