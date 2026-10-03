package com.teamam.quiz.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.domain.IntegrityEventType;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.repository.IntegrityEventRepository;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.resilience.ResilienceService;
import com.teamam.quiz.session.SessionStateService;
import com.teamam.quiz.timing.TimingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Not @Transactional: answers go through ResilienceService, which commits in its own transaction. */
@SpringBootTest
class IntegrityServiceTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private IntegrityService integrity;

    @Autowired
    private IntegrityEventRepository events;

    @Autowired
    private ResilienceService resilience;

    @Autowired
    private SessionStateService state;

    @Autowired
    private ResponseRepository responses;

    @Autowired
    private QuestionBankService bank;

    // Guards against: a tab-switch signal being lost, pinned on the wrong student/question,
    // or quietly changing a mark (signals are advisory only).
    @Test
    void attributesBlurEventWithoutAlteringScore() {
        String rollNo = "CS23B901";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        int positionOfC = state.currentState(quiz.sessionId, rollNo, TestQuiz.deviceOf(rollNo))
                .currentQuestion().options().indexOf("C");
        resilience.accept(quiz.sessionId, quiz.answer("sub-i1", rollNo, positionOfC));

        IntegrityOutcome outcome = integrity.record(quiz.sessionId,
                new IntegrityReport(rollNo, TestQuiz.deviceOf(rollNo), quiz.questionId, IntegrityEventType.FOCUS_LOSS));

        assertThat(outcome).isEqualTo(IntegrityOutcome.RECORDED);
        assertThat(events.countBySessionIdAndQuestionIdAndRollNoAndEventType(
                quiz.sessionId, quiz.questionId, rollNo, IntegrityEventType.FOCUS_LOSS)).isEqualTo(1);
        assertThat(responses.findBySessionIdAndQuestionIdAndRollNo(quiz.sessionId, quiz.questionId, rollNo)
                .orElseThrow().getCorrect()).isTrue();
    }

    // Guards against: one student's phone filing signals against another student's roll number.
    @Test
    void rejectsReportFromAnotherDevice() {
        String rollNo = "CS23B902";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);

        IntegrityOutcome outcome = integrity.record(quiz.sessionId,
                new IntegrityReport(rollNo, "dev-someone-else", quiz.questionId, IntegrityEventType.PASTE));

        assertThat(outcome).isEqualTo(IntegrityOutcome.REJECTED);
        assertThat(events.countBySessionIdAndRollNo(quiz.sessionId, rollNo)).isZero();
    }

    // Guards against: noise from questions that aren't open (not released yet, or already closed).
    @Test
    void ignoresSignalsOutsideAnOpenQuestion() throws InterruptedException {
        String rollNo = "CS23B903";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 0, rollNo);
        Long unreleased = bank.add(quiz.sessionId, 2, QuestionType.MCQ, "later", TestQuiz.OPTIONS, 0, 60).getId();
        Thread.sleep(TimingService.GRACE_WINDOW.toMillis() + 300);

        assertThat(integrity.record(quiz.sessionId, new IntegrityReport(
                rollNo, TestQuiz.deviceOf(rollNo), quiz.questionId, IntegrityEventType.FOCUS_LOSS)))
                .isEqualTo(IntegrityOutcome.REJECTED);
        assertThat(integrity.record(quiz.sessionId, new IntegrityReport(
                rollNo, TestQuiz.deviceOf(rollNo), unreleased, IntegrityEventType.FOCUS_LOSS)))
                .isEqualTo(IntegrityOutcome.REJECTED);
    }

    // Guards against: one flaky phone (or a script) filling the database with thousands of events.
    @Test
    void capsStoredEventsPerStudentAndQuestion() {
        String rollNo = "CS23B904";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        IntegrityReport report =
                new IntegrityReport(rollNo, TestQuiz.deviceOf(rollNo), quiz.questionId, IntegrityEventType.FOCUS_LOSS);

        for (int i = 0; i < IntegrityService.MAX_EVENTS_PER_STUDENT_QUESTION + 10; i++) {
            integrity.record(quiz.sessionId, report);
        }

        assertThat(events.countBySessionIdAndRollNo(quiz.sessionId, rollNo))
                .isEqualTo(IntegrityService.MAX_EVENTS_PER_STUDENT_QUESTION);
        assertThat(integrity.record(quiz.sessionId, report)).isEqualTo(IntegrityOutcome.CAPPED);
    }
}
