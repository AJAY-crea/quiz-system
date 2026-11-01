package com.teamam.quiz.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.session.NotAParticipantException;
import com.teamam.quiz.session.SessionEngine;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MyMarksServiceTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private MyMarksService myMarks;

    @Autowired
    private SessionEngine engine;

    @Autowired
    private QuestionBankService bank;

    @Autowired
    private QuestionRepository questions;

    @Autowired
    private ResponseRepository responses;

    @Autowired
    private QuizSessionRepository sessions;

    // Guards against: marks leaking before she publishes them, marks leaking to another phone, and
    // an open question's result letting a student tell friends the answer while it's still running.
    @Test
    void marksStayPrivateUntilPublishedAndOpenQuestionsStayHidden() {
        String roll = "CS23B501";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, roll);
        QuizSession session = sessions.findById(quiz.sessionId).orElseThrow();
        Question open = questions.findById(quiz.questionId).orElseThrow();
        answer(session, open, roll, true, null);
        String device = TestQuiz.deviceOf(roll);

        assertThat(myMarks.forStudent(quiz.sessionId, roll, device).published()).isFalse();
        assertThat(myMarks.forStudent(quiz.sessionId, roll, device).rows()).isEmpty();

        myMarks.setPublished(quiz.sessionId, true);
        MyMarks published = myMarks.forStudent(quiz.sessionId, roll, device);
        assertThat(published.published()).isTrue();
        assertThat(published.rows()).isEmpty(); // the only question is still open

        assertThatThrownBy(() -> myMarks.forStudent(quiz.sessionId, roll, "someone-elses-phone"))
                .isInstanceOf(NotAParticipantException.class);
    }

    // Guards against: an unmarked typed answer showing as 0 instead of "being marked".
    @Test
    void closedQuestionsShowMarksAndWhatIsStillBeingMarked() {
        String roll = "CS23B502";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, roll);
        QuizSession session = sessions.findById(quiz.sessionId).orElseThrow();
        Question mcq = questions.findById(quiz.questionId).orElseThrow();
        mcq.setDurationSeconds(0);
        answer(session, mcq, roll, true, null);
        Question typed = closedTyped(quiz.sessionId, 2);
        answer(session, typed, roll, null, "my long answer");
        closedTyped(quiz.sessionId, 3);
        myMarks.setPublished(quiz.sessionId, true);

        MyMarks result = myMarks.forStudent(quiz.sessionId, roll, TestQuiz.deviceOf(roll));

        assertThat(result.rows()).extracting(MyMarks.Row::status).containsExactly("MARKED", "AWAITING_MARKS", "NOT_ANSWERED");
        assertThat(result.rows().get(0).marks()).isEqualTo(1.0);
        assertThat(result.rows().get(1).marks()).isNull();
        assertThat(result.total()).isEqualTo(1.0);
        assertThat(result.outOf()).isEqualTo(1 + 5 + 5);
    }

    /** A released typed question worth 5 marks whose time is already over. */
    private Question closedTyped(Long sessionId, int order) {
        Question q = bank.add(sessionId, order, QuestionType.TYPED, "Q" + order, null, null, 0, 5);
        engine.release(sessionId, q.getId());
        return q;
    }

    private void answer(QuizSession session, Question q, String roll, Boolean correct, String typed) {
        Response r = new Response(session, q, roll);
        r.setSubmittedAt(Instant.now());
        r.setCorrect(correct);
        r.setTypedAnswer(typed);
        responses.save(r);
    }
}
