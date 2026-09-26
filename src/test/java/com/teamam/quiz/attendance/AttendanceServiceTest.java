package com.teamam.quiz.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.roster.RosterService;
import com.teamam.quiz.session.SessionEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AttendanceServiceTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private AttendanceService attendance;

    @Autowired
    private SessionEngine engine;

    @Autowired
    private RosterService roster;

    @Autowired
    private QuestionBankService bank;

    @Autowired
    private ResponseRepository responses;

    // Guards against: wrong present/absent under the 50% rule, late answers not proving presence,
    // dual-device joins going unflagged, or a flag changing someone's status.
    @Test
    void producesExpectedPresentAbsentList() {
        QuizSession session = engine.createSession("attendance fixture");
        Long sid = session.getId();
        // A: all 4 · B: exactly 2 of 4 (50%) · C: 1 of 4 · D: never joins
        // E: switches phone mid-lecture (dual-device), 3 answers · F: 0 answers
        // G: joins on F's phone (dual-device), 2 answers, both late
        roster.uploadRoster(sid, List.of("CS23B601", "CS23B602", "CS23B603", "CS23B604", "CS23B605", "CS23B606", "CS23B607"));
        String ticket = TestQuiz.joinTicket(ctx, sid);
        roster.join(sid, "CS23B601", "dev-A", ticket);
        roster.join(sid, "CS23B602", "dev-B", ticket);
        roster.join(sid, "CS23B603", "dev-C", ticket);
        roster.join(sid, "CS23B605", "dev-E", ticket);
        roster.join(sid, "CS23B605", "dev-E2", ticket);
        roster.join(sid, "CS23B606", "dev-F", ticket);
        roster.join(sid, "CS23B607", "dev-F", ticket);

        List<Question> questions = new ArrayList<>();
        for (int order = 1; order <= 4; order++) {
            Question q = bank.add(sid, order, QuestionType.MCQ, "Q" + order, TestQuiz.OPTIONS, 0, 60);
            engine.release(sid, q.getId());
            questions.add(q);
        }
        answer(session, questions, "CS23B601", 4, true);
        answer(session, questions, "CS23B602", 2, true);
        answer(session, questions, "CS23B603", 1, true);
        answer(session, questions, "CS23B605", 3, true);
        answer(session, questions, "CS23B607", 2, false);

        AttendanceReport report = attendance.derive(sid);

        assertThat(report.releasedQuestions()).isEqualTo(4);
        assertThat(report.rows()).extracting(AttendanceRow::rollNo).isSorted();
        Map<String, AttendanceRow> byRoll =
                report.rows().stream().collect(Collectors.toMap(AttendanceRow::rollNo, Function.identity()));

        assertThat(byRoll.get("CS23B601").status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(byRoll.get("CS23B602").status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(byRoll.get("CS23B603").status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(byRoll.get("CS23B603").joined()).isTrue();
        assertThat(byRoll.get("CS23B604").status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(byRoll.get("CS23B604").joined()).isFalse();
        assertThat(byRoll.get("CS23B605").status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(byRoll.get("CS23B605").flags()).isNotEmpty();
        assertThat(byRoll.get("CS23B606").status()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(byRoll.get("CS23B607").status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(byRoll.get("CS23B607").flags()).isNotEmpty();
        assertThat(byRoll.get("CS23B601").flags()).isEmpty();
    }

    // Guards against: everyone being marked absent in a session where no question was released yet.
    @Test
    void fallsBackToJoinedWhenNoQuestionReleased() {
        QuizSession session = engine.createSession("no questions yet");
        roster.uploadRoster(session.getId(), List.of("CS23B611", "CS23B612"));
        roster.join(session.getId(), "CS23B611", "dev-1", TestQuiz.joinTicket(ctx, session.getId()));

        AttendanceReport report = attendance.derive(session.getId());

        assertThat(report.basis()).isEqualTo(AttendanceReport.BASIS_JOINED_ONLY);
        assertThat(report.rows()).extracting(AttendanceRow::status)
                .containsExactly(AttendanceStatus.PRESENT, AttendanceStatus.ABSENT);
    }

    private void answer(QuizSession session, List<Question> questions, String rollNo, int count, boolean onTime) {
        for (int i = 0; i < count; i++) {
            Response response = new Response(session, questions.get(i), rollNo);
            response.setSubmissionId(rollNo + "-" + i);
            response.setSubmittedAt(java.time.Instant.now());
            response.setOnTime(onTime);
            responses.save(response);
        }
    }
}
