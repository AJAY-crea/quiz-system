package com.teamam.quiz.doubts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    // Guards against: a classmate learning who asked, or one student inflating a doubt by tapping
    // repeatedly (design doc §3: 5 doubts, 1 repeat upvote). Checks the JSON itself for the author.
    @Test
    void keepsAuthorsHiddenAndCountsUpvoteOnce() throws Exception {
        String asker = "CS23B961";
        String voter = "CS23B962";
        String other = "CS23B963";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, asker, voter, other);
        for (int i = 1; i <= 5; i++) {
            doubts.submit(quiz.sessionId, asker, TestQuiz.deviceOf(asker), "doubt " + i);
        }
        Long popular = idOf(quiz, voter, "doubt 3");

        doubts.upvote(quiz.sessionId, popular, voter, TestQuiz.deviceOf(voter));
        doubts.upvote(quiz.sessionId, popular, voter, TestQuiz.deviceOf(voter)); // the repeat
        doubts.upvote(quiz.sessionId, popular, other, TestQuiz.deviceOf(other));
        doubts.upvote(quiz.sessionId, idOf(quiz, voter, "doubt 5"), voter, TestQuiz.deviceOf(voter));

        DoubtQueue queue = doubts.queueFor(quiz.sessionId, voter, TestQuiz.deviceOf(voter));
        assertThat(queue.doubts()).hasSize(5);
        assertThat(queue.doubts()).extracting(DoubtQueue.Item::text)
                .containsExactly("doubt 3", "doubt 5", "doubt 1", "doubt 2", "doubt 4");
        assertThat(queue.doubts().get(0).upvotes()).isEqualTo(2);
        assertThat(queue.doubts().get(0).upvotedByMe()).isTrue();
        assertThat(queue.doubts().get(0).mine()).isFalse();
        assertThat(queue.doubts().get(2).upvotedByMe()).isFalse();
        assertThat(new ObjectMapper().writeValueAsString(queue)).doesNotContain(asker);

        // The instructor's queue has the same order, and stays anonymous by default.
        List<DoubtView> forHer = doubts.forInstructor(quiz.sessionId);
        assertThat(forHer.get(0).text()).isEqualTo("doubt 3");
        assertThat(forHer.get(0).upvotes()).isEqualTo(2);
        assertThat(forHer.get(0).authorRollNo()).isNull();
    }

    // Guards against: self-promotion, upvoting something already dealt with, or voting from another phone.
    @Test
    void refusesOwnDismissedAndWrongPhoneUpvotes() {
        String asker = "CS23B964";
        String voter = "CS23B965";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, asker, voter);
        doubts.submit(quiz.sessionId, asker, TestQuiz.deviceOf(asker), "mine");
        Long id = idOf(quiz, voter, "mine");

        assertThat(doubts.queueFor(quiz.sessionId, asker, TestQuiz.deviceOf(asker)).doubts().get(0).mine()).isTrue();
        assertThatThrownBy(() -> doubts.upvote(quiz.sessionId, id, asker, TestQuiz.deviceOf(asker)))
                .isInstanceOf(DoubtRefusedException.class);
        assertThatThrownBy(() -> doubts.upvote(quiz.sessionId, id, voter, "dev-other"))
                .isInstanceOf(NotAParticipantException.class);
        assertThatThrownBy(() -> doubts.queueFor(quiz.sessionId, voter, "dev-other"))
                .isInstanceOf(NotAParticipantException.class);

        doubts.dismiss(quiz.sessionId, id);
        assertThat(doubts.queueFor(quiz.sessionId, voter, TestQuiz.deviceOf(voter)).doubts()).isEmpty();
        assertThatThrownBy(() -> doubts.upvote(quiz.sessionId, id, voter, TestQuiz.deviceOf(voter)))
                .isInstanceOf(DoubtRefusedException.class);
    }

    // Guards against: an abusive doubt spreading to the class with no way to stop it.
    @Test
    void instructorCanStopSharingDoubtsWithTheClass() {
        String asker = "CS23B966";
        String voter = "CS23B967";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, asker, voter);
        doubts.submit(quiz.sessionId, asker, TestQuiz.deviceOf(asker), "visible?");
        Long id = idOf(quiz, voter, "visible?");

        doubts.setShared(quiz.sessionId, false);

        DoubtQueue queue = doubts.queueFor(quiz.sessionId, voter, TestQuiz.deviceOf(voter));
        assertThat(queue.shared()).isFalse();
        assertThat(queue.doubts()).isEmpty();
        assertThatThrownBy(() -> doubts.upvote(quiz.sessionId, id, voter, TestQuiz.deviceOf(voter)))
                .isInstanceOf(DoubtRefusedException.class);
        assertThat(doubts.forInstructor(quiz.sessionId)).hasSize(1); // she still sees it
    }

    /** The id of a doubt, found as a student would find it. */
    private Long idOf(TestQuiz quiz, String rollNo, String text) {
        return doubts.queueFor(quiz.sessionId, rollNo, TestQuiz.deviceOf(rollNo)).doubts().stream()
                .filter(d -> d.text().equals(text)).findFirst().orElseThrow().id();
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
