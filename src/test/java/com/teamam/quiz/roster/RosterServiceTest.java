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
    private QuizSessionRepository sessionRepository;

    @Test
    void rejectsUnlistedAndFlagsDeviceMismatch() {
        QuizSession session = sessionRepository.save(new QuizSession("Week 1 roster test session"));
        List<String> roster =
                IntStream.rangeClosed(1, 30).mapToObj(i -> String.format("CS23B%03d", i)).toList();
        rosterService.uploadRoster(session.getId(), roster);

        String firstRollNo = roster.get(0);

        JoinResult listedJoin = rosterService.join(session.getId(), firstRollNo, "device-A");
        assertThat(listedJoin.outcome()).isEqualTo(JoinOutcome.ACCEPTED);

        JoinResult unlistedJoin = rosterService.join(session.getId(), "CS23B999", "device-B");
        assertThat(unlistedJoin.outcome()).isEqualTo(JoinOutcome.REJECTED_UNLISTED);

        JoinResult deviceMismatchJoin = rosterService.join(session.getId(), firstRollNo, "device-C");
        assertThat(deviceMismatchJoin.outcome()).isEqualTo(JoinOutcome.ACCEPTED_FLAGGED);
        assertThat(deviceMismatchJoin.flagReason()).isNotBlank();

        String secondRollNo = roster.get(1);
        JoinResult tokenReuseJoin = rosterService.join(session.getId(), secondRollNo, "device-C");
        assertThat(tokenReuseJoin.outcome()).isEqualTo(JoinOutcome.ACCEPTED_FLAGGED);
        assertThat(tokenReuseJoin.flagReason()).isNotBlank();
    }
}
