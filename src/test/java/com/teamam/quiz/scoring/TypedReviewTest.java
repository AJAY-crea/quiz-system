package com.teamam.quiz.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.session.SessionEngine;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TypedReviewTest {

    @Autowired
    private ScoringService scoring;

    @Autowired
    private SessionEngine engine;

    @Autowired
    private QuestionBankService bank;

    @Autowired
    private ResponseRepository responses;

    // Guards against: one mark landing on genuinely different answers because grouping was too eager.
    @Test
    void groupsOnlyAnswersThatSayTheSameThing() {
        assertThat(ScoringService.normalise("V = IR.")).isEqualTo(ScoringService.normalise("  v=ir "));
        assertThat(ScoringService.normalise("Ohm's  Law")).isEqualTo(ScoringService.normalise("ohm's law"));
        assertThat(ScoringService.normalise("1 2")).isNotEqualTo(ScoringService.normalise("12"));
        assertThat(ScoringService.normalise("-5")).isNotEqualTo(ScoringService.normalise("5"));
        assertThat(ScoringService.normalise("x+1")).isNotEqualTo(ScoringService.normalise("x-1"));
    }

    // Guards against: the teacher marking the same wording over and over, or a late answer sneaking
    // into a group and earning marks.
    @Test
    void marksAGroupOnceAndKeepsLateAnswersOut() {
        Fixture f = fixture(5);
        answer(f, "CS23B401", "V = IR", true);
        answer(f, "CS23B402", "v=ir.", true);
        answer(f, "CS23B403", "Voltage is current times resistance", true);
        answer(f, "CS23B404", "V = IR", false);

        TypedReview review = scoring.groupTyped(f.session.getId(), f.question.getId());
        assertThat(review.pending()).isEqualTo(3);
        assertThat(review.groups()).hasSize(2);
        TypedReview.Group biggest = review.groups().get(0);
        assertThat(biggest.count()).isEqualTo(2);
        assertThat(review.late()).extracting(TypedReview.Answer::rollNo).containsExactly("CS23B404");

        int updated = scoring.markGroup(f.session.getId(), f.question.getId(), biggest.key(), 4.5);

        assertThat(updated).isEqualTo(2);
        TypedReview after = scoring.groupTyped(f.session.getId(), f.question.getId());
        assertThat(after.marked()).isEqualTo(2);
        assertThat(after.pending()).isEqualTo(1);
        assertThat(after.groups().get(0).marks()).isEqualTo(4.5);
        assertThat(after.late().get(0).marks()).isNull();
    }

    // Guards against: the teacher being unable to treat one student differently within a group, or
    // the screen pretending the group still has one mark afterwards.
    @Test
    void oneAnswerCanBeMarkedDifferentlyFromItsGroup() {
        Fixture f = fixture(5);
        answer(f, "CS23B411", "same", true);
        Response second = answer(f, "CS23B412", "same", true);
        scoring.markGroup(f.session.getId(), f.question.getId(), "same", 3.0);

        scoring.markOne(f.session.getId(), second.getId(), 5.0);

        TypedReview.Group group = scoring.groupTyped(f.session.getId(), f.question.getId()).groups().get(0);
        assertThat(group.mixed()).isTrue();
        assertThat(group.marks()).isNull();
    }

    // Guards against: marks above the maximum, marks on a late answer, or hand marks on an MCQ.
    @Test
    void refusesInvalidMarks() {
        Fixture f = fixture(5);
        answer(f, "CS23B421", "fine", true);
        Response late = answer(f, "CS23B422", "too late", false);
        Long sid = f.session.getId();
        Long qid = f.question.getId();

        assertThatThrownBy(() -> scoring.markGroup(sid, qid, "fine", 5.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scoring.markGroup(sid, qid, "fine", -1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scoring.markGroup(sid, qid, "fine", Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scoring.markOne(sid, late.getId(), 1.0)).isInstanceOf(IllegalStateException.class);

        Question mcq = bank.add(sid, 2, QuestionType.MCQ, "m", TestQuiz.OPTIONS, 0, 60);
        assertThatThrownBy(() -> scoring.groupTyped(sid, mcq.getId())).isInstanceOf(IllegalArgumentException.class);
    }

    // Guards against: an answer scoring something before the teacher has marked it, and clearing a
    // mark not sending it back to "waiting".
    @Test
    void marksForFollowsTheRules() {
        Fixture f = fixture(5);
        Response typed = answer(f, "CS23B431", "text", true);
        assertThat(ScoringService.marksFor(f.question, typed)).isNull();
        assertThat(ScoringService.awaitsMarking(f.question, typed)).isTrue();

        scoring.markOne(f.session.getId(), typed.getId(), 3.5);
        assertThat(ScoringService.marksFor(f.question, typed)).isEqualTo(3.5);

        scoring.markOne(f.session.getId(), typed.getId(), null);
        assertThat(ScoringService.awaitsMarking(f.question, typed)).isTrue();

        assertThat(ScoringService.marksFor(f.question, answer(f, "CS23B432", "late", false))).isEqualTo(0.0);
        assertThat(ScoringService.marksFor(f.question, null)).isNull();

        Question mcq = bank.add(f.session.getId(), 2, QuestionType.MCQ, "m", TestQuiz.OPTIONS, 0, 60, 2);
        Response right = new Response(f.session, mcq, "CS23B431");
        right.setCorrect(true);
        assertThat(ScoringService.marksFor(mcq, right)).isEqualTo(2.0);
    }

    private record Fixture(QuizSession session, Question question) {
    }

    /** A session with one released typed question worth the given marks. */
    private Fixture fixture(int maxMarks) {
        QuizSession session = engine.createSession("typed review");
        Question question = bank.add(session.getId(), 1, QuestionType.TYPED, "Explain Ohm's law", null, null, 60, maxMarks);
        engine.release(session.getId(), question.getId());
        return new Fixture(session, question);
    }

    /** Stores a typed answer as if it had arrived on time or late. */
    private Response answer(Fixture f, String rollNo, String text, boolean onTime) {
        Response response = new Response(f.session, f.question, rollNo);
        response.setSubmissionId(rollNo);
        response.setSubmittedAt(Instant.now());
        response.setOnTime(onTime);
        response.setTypedAnswer(text);
        return responses.save(response);
    }
}
