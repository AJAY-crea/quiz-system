package com.teamam.quiz.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.resilience.AcceptOutcome;
import com.teamam.quiz.resilience.AnswerSubmission;
import com.teamam.quiz.resilience.ResilienceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Not @Transactional: answers go through ResilienceService, which commits in its own transaction. */
@SpringBootTest
class ReopenQuestionTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private SessionEngine engine;

    @Autowired
    private SessionStateService state;

    @Autowired
    private ResilienceService resilience;

    @Autowired
    private QuestionBankService bank;

    @Autowired
    private ResponseRepository responses;

    // Guards against: a reopened question not reaching phones, answers already given being lost, a
    // changed choice being refused as late, cutting off an open question, or the release order breaking.
    @Test
    void anEarlierQuestionCanBeMadeLiveAgainOnceTheCurrentOneIsOver() throws InterruptedException {
        String roll = "CS23B701";
        String device = TestQuiz.deviceOf(roll);
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 1, roll);
        int a = state.currentState(quiz.sessionId, roll, device).currentQuestion().options().indexOf("A");
        int c = state.currentState(quiz.sessionId, roll, device).currentQuestion().options().indexOf("C");
        resilience.accept(quiz.sessionId, tap(quiz, "first", roll, a, 1));

        Question q2 = bank.add(quiz.sessionId, 2, QuestionType.MCQ, "Second", TestQuiz.OPTIONS, 0, 1);
        engine.release(quiz.sessionId, q2.getId());
        assertThatThrownBy(() -> engine.reopen(quiz.sessionId, quiz.questionId, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still open");
        Thread.sleep(1500); // Q1 and Q2 are both over now

        engine.reopen(quiz.sessionId, quiz.questionId, 60);

        SessionStateView view = state.currentState(quiz.sessionId, roll, device);
        assertThat(view.currentQuestion().id()).isEqualTo(quiz.questionId);
        assertThat(view.remainingMs()).isGreaterThan(50_000);
        assertThat(view.myChoice()).containsExactly(a); // the earlier answer is kept and shown
        assertThat(resilience.accept(quiz.sessionId, tap(quiz, "second", roll, c, 2)).outcome())
                .isEqualTo(AcceptOutcome.ACCEPTED);
        assertThat(responses.findBySessionIdAndQuestionIdAndRollNo(quiz.sessionId, quiz.questionId, roll)
                .orElseThrow().getCorrect()).isTrue(); // C is the key in TestQuiz

        Question q3 = bank.add(quiz.sessionId, 3, QuestionType.MCQ, "Third", TestQuiz.OPTIONS, 0, 60);
        engine.release(quiz.sessionId, q3.getId()); // still in order after a reopen
        assertThat(state.currentState(quiz.sessionId, roll, device).currentQuestion().id()).isEqualTo(q3.getId());
        assertThatThrownBy(() -> engine.reopen(quiz.sessionId, q2.getId(), 30)).isInstanceOf(IllegalStateException.class);
    }

    private static AnswerSubmission tap(TestQuiz quiz, String id, String roll, int position, long seq) {
        return new AnswerSubmission(id, roll, TestQuiz.deviceOf(roll), quiz.questionId, position, null, null, seq);
    }
}
