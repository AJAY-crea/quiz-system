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

    /** A tap on a choice question, as the phone sends it: with a seq ordering the student's taps. */
    private static AnswerSubmission tap(TestQuiz quiz, String id, String rollNo, int shuffledIndex, long seq) {
        return new AnswerSubmission(id, rollNo, TestQuiz.deviceOf(rollNo), quiz.questionId, shuffledIndex, null, null, seq);
    }

    // Guards against: a student who changes their mind before time is up being scored on the first tap.
    @Test
    void latestTapBeforeTheDeadlineIsTheAnswer() {
        String rollNo = "CS23B111";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);

        assertThat(resilience.accept(quiz.sessionId, tap(quiz, "t1", rollNo, 0, 1)).outcome()).isEqualTo(ACCEPTED);
        AcceptResult change = resilience.accept(quiz.sessionId, tap(quiz, "t2", rollNo, 1, 2));

        assertThat(change.outcome()).isEqualTo(ACCEPTED);
        assertThat(storedCount(quiz, rollNo)).isEqualTo(1);
        assertThat(stored(quiz, rollNo).getSubmissionId()).isEqualTo("t2");
        assertThat(stored(quiz, rollNo).getAnswerSeq()).isEqualTo(2L);
    }

    // Guards against: a slow resend of an OLD tap arriving after a newer one and undoing it (the
    // network reorders messages after a reconnect).
    @Test
    void anOlderTapArrivingLateCannotUndoANewerOne() {
        String rollNo = "CS23B112";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        resilience.accept(quiz.sessionId, tap(quiz, "t1", rollNo, 0, 1));
        resilience.accept(quiz.sessionId, tap(quiz, "t3", rollNo, 2, 3));

        AcceptResult stale = resilience.accept(quiz.sessionId, tap(quiz, "t2", rollNo, 1, 2));

        assertThat(stale.outcome()).isEqualTo(DUPLICATE);
        assertThat(stale.sameSubmission()).isTrue(); // the phone stops resending it
        assertThat(stored(quiz, rollNo).getSubmissionId()).isEqualTo("t3");
    }

    // Guards against: changing an answer after time is up (the server's clock decides, not the phone).
    @Test
    void aChangeAfterTheDeadlineIsRefusedAndTheOnTimeAnswerStands() throws InterruptedException {
        String rollNo = "CS23B113";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 1, rollNo);
        resilience.accept(quiz.sessionId, tap(quiz, "t1", rollNo, 0, 1));
        Thread.sleep(1000 + TimingService.GRACE_WINDOW.toMillis() + 300);

        AcceptResult late = resilience.accept(quiz.sessionId, tap(quiz, "t2", rollNo, 1, 2));

        assertThat(late.outcome()).isEqualTo(DUPLICATE);
        assertThat(late.sameSubmission()).isFalse();
        assertThat(stored(quiz, rollNo).getSubmissionId()).isEqualTo("t1");
        assertThat(stored(quiz, rollNo).isOnTime()).isTrue();
    }

    // Guards against: several-answer questions being refused, or scored on the wrong set.
    @Test
    void severalAnswerQuestionAcceptsTheTickedSet() {
        String rollNo = "CS23B114";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        Question multi = bank.addQuestion(quiz.sessionId, 2, QuestionType.MULTI, "Pick A and C",
                TestQuiz.OPTIONS, List.of(0, 2), 60, 1);
        ctx.getBean(com.teamam.quiz.session.SessionEngine.class).release(quiz.sessionId, multi.getId());
        List<String> mine = ctx.getBean(com.teamam.quiz.questionbank.RandomisationService.class).shuffleFor(rollNo, multi);
        List<Integer> aAndC = List.of(mine.indexOf("A"), mine.indexOf("C"));

        AcceptResult result = resilience.accept(quiz.sessionId,
                new AnswerSubmission("m1", rollNo, TestQuiz.deviceOf(rollNo), multi.getId(), null, null, aAndC, 1L));
        AcceptResult empty = resilience.accept(quiz.sessionId,
                new AnswerSubmission("m2", rollNo, TestQuiz.deviceOf(rollNo), multi.getId(), null, null, List.of(), 2L));

        assertThat(result.outcome()).isEqualTo(ACCEPTED);
        assertThat(empty.outcome()).isEqualTo(REJECTED);
        Response saved = responses.findBySessionIdAndQuestionIdAndRollNo(quiz.sessionId, multi.getId(), rollNo).orElseThrow();
        assertThat(saved.getSelectedOptions()).containsExactly(0, 2);
        assertThat(saved.getCorrect()).isTrue();
    }

    // Guards against: a resent answer without a seq (a typed answer, or an older page) overwriting the
    // first one: those stay "first answer stands".
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
