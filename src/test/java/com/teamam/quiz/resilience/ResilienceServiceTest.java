package com.teamam.quiz.resilience;

import static com.teamam.quiz.resilience.AcceptOutcome.ACCEPTED;
import static com.teamam.quiz.resilience.AcceptOutcome.DUPLICATE;
import static com.teamam.quiz.resilience.AcceptOutcome.REJECTED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.roster.RosterService;
import com.teamam.quiz.timing.TimingService;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Deliberately NOT @Transactional: accept() commits in its own transaction, and the concurrency
 * test needs two threads to see each other's writes. Each test uses its own session instead.
 */
@SpringBootTest
class ResilienceServiceTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private ResilienceService resilience;

    @Autowired
    private ResponseRepository responses;

    @Autowired
    private RosterService roster;

    @Autowired
    private QuestionBankService bank;

    // Guards against: a resend after a lost ack being stored as a second answer.
    @Test
    void resentAnswerRecordedOnce() {
        String rollNo = "CS23B101";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        AnswerSubmission submission = quiz.answer("sub-1", rollNo, 0);

        assertThat(resilience.accept(quiz.sessionId, submission).outcome()).isEqualTo(ACCEPTED);

        AcceptResult resend = resilience.accept(quiz.sessionId, submission);
        assertThat(resend.outcome()).isEqualTo(DUPLICATE);
        assertThat(resend.sameSubmission()).isTrue();
        assertThat(storedCount(quiz, rollNo)).isEqualTo(1);
    }

    // Guards against: the check-then-insert race when two copies of one answer arrive together.
    @Test
    void concurrentDuplicatesRecordedOnce() throws Exception {
        String rollNo = "CS23B102";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        AnswerSubmission submission = quiz.answer("sub-2", rollNo, 1);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<AcceptResult> send = () -> {
                startGate.await();
                return resilience.accept(quiz.sessionId, submission);
            };
            Future<AcceptResult> first = pool.submit(send);
            Future<AcceptResult> second = pool.submit(send);
            startGate.countDown();

            assertThat(List.of(first.get(10, SECONDS).outcome(), second.get(10, SECONDS).outcome()))
                    .containsExactlyInAnyOrder(ACCEPTED, DUPLICATE);
        } finally {
            pool.shutdownNow();
        }
        assertThat(storedCount(quiz, rollNo)).isEqualTo(1);
    }

    // Guards against: a student overwriting their first answer (the team decided first is final).
    @Test
    void differentSecondAnswerIsDuplicateAndFirstStands() {
        String rollNo = "CS23B103";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);

        assertThat(resilience.accept(quiz.sessionId, quiz.answer("sub-3a", rollNo, 0)).outcome())
                .isEqualTo(ACCEPTED);
        AcceptResult second = resilience.accept(quiz.sessionId, quiz.answer("sub-3b", rollNo, 1));

        assertThat(second.outcome()).isEqualTo(DUPLICATE);
        assertThat(second.sameSubmission()).isFalse();
        assertThat(stored(quiz, rollNo).getSubmissionId()).isEqualTo("sub-3a");
    }

    // Guards against: a late answer being thrown away (evidence lost) or silently counted as on time.
    @Test
    void lateAnswerStoredButMarkedLate() throws InterruptedException {
        String rollNo = "CS23B104";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 0, rollNo);
        Thread.sleep(TimingService.GRACE_WINDOW.toMillis() + 300);

        AcceptResult result = resilience.accept(quiz.sessionId, quiz.answer("sub-4", rollNo, 0));

        assertThat(result.outcome()).isEqualTo(ACCEPTED);
        assertThat(result.onTime()).isFalse();
        assertThat(stored(quiz, rollNo).isOnTime()).isFalse();
    }

    // Guards against: submitting for a roll number from a device it is no longer bound to.
    @Test
    void rejectsStaleDeviceToken() {
        String rollNo = "CS23B105";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        roster.join(quiz.sessionId, rollNo, "dev-another-phone", TestQuiz.joinTicket(ctx, quiz.sessionId));

        AcceptResult result = resilience.accept(quiz.sessionId, quiz.answer("sub-5", rollNo, 0));

        assertThat(result.outcome()).isEqualTo(REJECTED);
        assertThat(storedCount(quiz, rollNo)).isZero();
    }

    // Guards against: answering a question the instructor has not released yet.
    @Test
    void rejectsUnreleasedQuestion() {
        String rollNo = "CS23B106";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        Question next = bank.add(quiz.sessionId, 2, QuestionType.MCQ, "Next", TestQuiz.OPTIONS, 0, 60);

        AcceptResult result = resilience.accept(
                quiz.sessionId,
                new AnswerSubmission("sub-6", rollNo, TestQuiz.deviceOf(rollNo), next.getId(), 0, null));

        assertThat(result.outcome()).isEqualTo(REJECTED);
    }

    // Guards against: a bad option index crashing remap or being stored as a real answer.
    @Test
    void rejectsOutOfRangeOption() {
        String rollNo = "CS23B107";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);

        AcceptResult result = resilience.accept(quiz.sessionId, quiz.answer("sub-7", rollNo, 7));

        assertThat(result.outcome()).isEqualTo(REJECTED);
        assertThat(storedCount(quiz, rollNo)).isZero();
    }

    private long storedCount(TestQuiz quiz, String rollNo) {
        return responses.countBySessionIdAndQuestionIdAndRollNo(quiz.sessionId, quiz.questionId, rollNo);
    }

    private Response stored(TestQuiz quiz, String rollNo) {
        return responses
                .findBySessionIdAndQuestionIdAndRollNo(quiz.sessionId, quiz.questionId, rollNo)
                .orElseThrow();
    }
}
