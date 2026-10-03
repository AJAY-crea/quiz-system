package com.teamam.quiz.doubts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.session.NotAParticipantException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DoubtsServiceTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private DoubtsService doubts;

    // Guards against: a doubt reaching the instructor without the question it was about, or exposing
    // its author when the instructor hasn't chosen to see authors.
    @Test
    void doubtIsTaggedToOpenQuestionAndAnonymousByDefault() {
        String rollNo = "CS23B951";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);

        doubts.submit(quiz.sessionId, rollNo, TestQuiz.deviceOf(rollNo), "Why is the answer not B?");
        List<DoubtView> list = doubts.forInstructor(quiz.sessionId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).questionId()).isEqualTo(quiz.questionId);
        assertThat(list.get(0).questionOrder()).isEqualTo(1);
        assertThat(list.get(0).authorRollNo()).isNull();

        doubts.setAuthorsVisible(quiz.sessionId, true);
        assertThat(doubts.forInstructor(quiz.sessionId).get(0).authorRollNo()).isEqualTo(rollNo);
    }

    // Guards against: dismissed doubts cluttering the instructor's open list.
    @Test
    void dismissMovesDoubtOutOfTheOpenList() {
        String rollNo = "CS23B952";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        doubts.submit(quiz.sessionId, rollNo, TestQuiz.deviceOf(rollNo), "first");
        doubts.submit(quiz.sessionId, rollNo, TestQuiz.deviceOf(rollNo), "second");
        Long firstId = doubts.forInstructor(quiz.sessionId).stream()
                .filter(d -> d.text().equals("first")).findFirst().orElseThrow().id();

        doubts.dismiss(quiz.sessionId, firstId);

        List<DoubtView> list = doubts.forInstructor(quiz.sessionId);
        assertThat(list.get(0).text()).isEqualTo("second");
        assertThat(list.get(0).dismissed()).isFalse();
        assertThat(list.get(1).dismissed()).isTrue();
    }

    // Guards against: the channel being misused (design doc risk #3): the instructor can switch it
    // off, outsiders can't post, and one student can't flood it.
    @Test
    void refusesWhenDisabledFromOutsidersAndBeyondLimits() {
        String rollNo = "CS23B953";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);
        String device = TestQuiz.deviceOf(rollNo);

        assertThatThrownBy(() -> doubts.submit(quiz.sessionId, rollNo, "dev-other", "hi"))
                .isInstanceOf(NotAParticipantException.class);
        assertThatThrownBy(() -> doubts.submit(quiz.sessionId, rollNo, device, "x".repeat(DoubtsService.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> doubts.submit(quiz.sessionId, rollNo, device, "   "))
                .isInstanceOf(IllegalArgumentException.class);

        for (int i = 0; i < DoubtsService.MAX_PER_STUDENT; i++) {
            doubts.submit(quiz.sessionId, rollNo, device, "doubt " + i);
        }
        assertThatThrownBy(() -> doubts.submit(quiz.sessionId, rollNo, device, "one too many"))
                .isInstanceOf(DoubtRefusedException.class);

        doubts.setEnabled(quiz.sessionId, false);
        assertThatThrownBy(() -> doubts.submit(quiz.sessionId, rollNo, device, "channel is off"))
                .isInstanceOf(DoubtRefusedException.class)
                .hasMessageContaining("turned off");
    }
}
