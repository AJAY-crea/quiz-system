package com.teamam.quiz.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.resilience.ResilienceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class SessionStateServiceTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private SessionStateService state;

    @Autowired
    private ResilienceService resilience;

    @Autowired
    private ResponseRepository responses;

    // Guards against: a reconnecting student getting the wrong option order, a wrong timer,
    // or being asked to answer a question they already answered.
    @Test
    void stateGivesPersonalShuffleRemainingTimeAndAnsweredFlag() {
        String rollNo = "CS23B401";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);

        SessionStateView before = state.currentState(quiz.sessionId, rollNo, TestQuiz.deviceOf(rollNo));
        assertThat(before.currentQuestion().id()).isEqualTo(quiz.questionId);
        assertThat(before.currentQuestion().options()).containsExactlyInAnyOrderElementsOf(TestQuiz.OPTIONS);
        assertThat(before.remainingMs()).isBetween(1L, 60_000L);
        assertThat(before.alreadyAnswered()).isFalse();

        // Picking "C" at the position this student's own view shows it must score as correct:
        // proves the state view uses the same shuffle that remap() inverts.
        int positionOfC = before.currentQuestion().options().indexOf("C");
        resilience.accept(quiz.sessionId, quiz.answer("sub-st1", rollNo, positionOfC));
        assertThat(responses
                        .findBySessionIdAndQuestionIdAndRollNo(quiz.sessionId, quiz.questionId, rollNo)
                        .orElseThrow()
                        .getCorrect())
                .isTrue();

        assertThat(state.currentState(quiz.sessionId, rollNo, TestQuiz.deviceOf(rollNo)).alreadyAnswered())
                .isTrue();
    }

    // Guards against: showing a student's view to a device that isn't bound to that roll number.
    @Test
    void stateRefusesWrongDeviceToken() {
        String rollNo = "CS23B402";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);

        assertThatThrownBy(() -> state.currentState(quiz.sessionId, rollNo, "dev-wrong"))
                .isInstanceOf(NotAParticipantException.class);
    }

    // Guards against: a reloaded page (or a cleared phone) showing no tick, so the student thinks their
    // answer was lost, or ticking the wrong option because shuffles differ per student.
    @Test
    void showsTheStoredChoiceInTheStudentsOwnOrder() {
        String rollNo = "CS23B209";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        int positionOfC = state.currentState(quiz.sessionId, rollNo, TestQuiz.deviceOf(rollNo))
                .currentQuestion().options().indexOf("C");
        resilience.accept(quiz.sessionId, quiz.answer("s-209", rollNo, positionOfC));

        SessionStateView view = state.currentState(quiz.sessionId, rollNo, TestQuiz.deviceOf(rollNo));

        assertThat(view.alreadyAnswered()).isTrue();
        assertThat(view.myChoice()).containsExactly(positionOfC);
    }
}
