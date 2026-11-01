package com.teamam.quiz.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.session.SessionEngine;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AnalyticsServiceTest {

    @Autowired
    private AnalyticsService analytics;

    @Autowired
    private SessionEngine engine;

    @Autowired
    private QuestionBankService bank;

    @Autowired
    private ResponseRepository responses;

    /**
     * Hand-computed, 20 students, 3 one-mark MCQs (option 0 is correct on each):
     *   Q2 right: s01-s15.  Q3 right: s01-s05.  Q1 right: s01-s04, s06-s11, s16.
     * Totals: s01-s04 = 3; s05-s11 = 2; s12-s16 = 1; s17-s20 = 0.
     * Groups are 27% of 20, rounded = 5. Top = s01-s05. Bottom = s16-s20 (ties break by roll number).
     *   Q1: top 4/5, bottom 1/5, so D = 0.8 - 0.2 = 0.6, and 11/20 = 55% correct.
     *   Q2: top 5/5, bottom 0/5, so D = 1.0, and 15/20 = 75%.
     *   Q3: top 5/5, bottom 0/5, so D = 1.0, and 5/20 = 25%.
     * Guards against: a wrong formula, or ranking that changes between runs.
     */
    @Test
    void computesDiscriminationIndexOnFixture() {
        QuizSession session = engine.createSession("analytics fixture");
        Long sid = session.getId();
        List<Question> questions = List.of(mcq(sid, 1, 0), mcq(sid, 2, 0), mcq(sid, 3, 0));
        for (int s = 1; s <= 20; s++) {
            String roll = String.format("S%02d", s);
            boolean q1 = s <= 4 || (s >= 6 && s <= 11) || s == 16;
            answer(session, questions.get(0), roll, q1 ? 0 : 1, true);
            answer(session, questions.get(1), roll, s <= 15 ? 0 : 2, true);
            answer(session, questions.get(2), roll, s <= 5 ? 0 : 3, true);
        }

        List<QuestionAnalytics> result = analytics.forSession(sid);

        assertThat(result.get(0).discrimination()).isEqualTo(0.6);
        assertThat(result.get(0).percentCorrect()).isEqualTo(55.0);
        assertThat(result.get(1).discrimination()).isEqualTo(1.0);
        assertThat(result.get(1).percentCorrect()).isEqualTo(75.0);
        assertThat(result.get(2).discrimination()).isEqualTo(1.0);
        assertThat(result.get(2).percentCorrect()).isEqualTo(25.0);
        assertThat(analytics.forQuestion(questions.get(0).getId()).discrimination()).isEqualTo(0.6);

        // Distractor pull on Q3: 5 right, 15 chose option 3, none chose 1 or 2.
        List<QuestionAnalytics.OptionStat> options = result.get(2).options();
        assertThat(options).extracting(QuestionAnalytics.OptionStat::count).containsExactly(5L, 0L, 0L, 15L);
        assertThat(options.get(3).percent()).isEqualTo(75.0);
        assertThat(options.get(0).correct()).isTrue();
        assertThat(result.get(2).notes()).anyMatch(n -> n.startsWith("Hard"));
        assertThat(result.get(2).notes()).anyMatch(n -> n.contains("\"D\""));
    }

    // Guards against: a wrong answer key going unnoticed, the case analytics exist to catch.
    // Key says option 0, but strong students (who ace Q1) all pick 1 on Q2.
    @Test
    void negativeDiscriminationWarnsAboutTheKeyAndFixingItRescores() {
        QuizSession session = engine.createSession("wrong key");
        Long sid = session.getId();
        Question anchor = mcq(sid, 1, 0);
        Question q2 = mcq(sid, 2, 0);
        Question q3 = mcq(sid, 3, 0);
        for (int s = 1; s <= 4; s++) {
            String roll = "K" + s;
            boolean strong = s <= 2;
            answer(session, anchor, roll, strong ? 0 : 1, true);
            answer(session, q3, roll, strong ? 0 : 1, true);
            answer(session, q2, roll, strong ? 1 : 0, true);
        }
        assertThat(analytics.forQuestion(q2.getId()).discrimination()).isNegative();
        assertThat(analytics.forQuestion(q2.getId()).notes()).anyMatch(n -> n.contains("answer key"));

        bank.changeAnswerKey(sid, q2.getId(), 1);

        assertThat(analytics.forQuestion(q2.getId()).discrimination()).isEqualTo(1.0);
        assertThat(analytics.forQuestion(q2.getId()).options().get(1).correct()).isTrue();
    }

    // Guards against: figures that look precise but mean nothing, from a tiny class or half-marked answers.
    @Test
    void discriminationIsWithheldWhenItCannotBeJudged() {
        QuizSession small = engine.createSession("small class");
        Question q = mcq(small.getId(), 1, 0);
        answer(small, q, "A1", 0, true);
        answer(small, q, "A2", 1, true);
        assertThat(analytics.forQuestion(q.getId()).discrimination()).isNull();
        assertThat(analytics.forQuestion(q.getId()).percentCorrect()).isEqualTo(50.0);

        QuizSession typedSession = engine.createSession("typed");
        Question typed = bank.add(typedSession.getId(), 1, QuestionType.TYPED, "Why?", null, null, 60, 4);
        engine.release(typedSession.getId(), typed.getId());
        for (int s = 1; s <= 4; s++) {
            Response r = new Response(typedSession, typed, "T" + s);
            r.setOnTime(true);
            r.setSubmittedAt(Instant.now());
            r.setTypedAnswer("answer " + s);
            r.setAwardedMarks(s == 1 ? 2.0 : null);
            responses.save(r);
        }
        QuestionAnalytics result = analytics.forQuestion(typed.getId());
        assertThat(result.discrimination()).isNull();
        assertThat(result.awaitingMarks()).isEqualTo(3);
        assertThat(result.percentCorrect()).isEqualTo(50.0); // only the marked one: 2 of 4 marks
    }

    // Guards against: analytics not being "kept across the semester" as the design doc promises.
    @Test
    void semesterViewListsEverySessionWithItsHardestQuestion() {
        QuizSession session = engine.createSession("week 9");
        Question easy = mcq(session.getId(), 1, 0);
        Question hard = mcq(session.getId(), 2, 0);
        answer(session, easy, "M1", 0, true);
        answer(session, hard, "M1", 1, true);

        SessionSummary summary = analytics.semester().stream()
                .filter(s -> s.sessionId().equals(session.getId())).findFirst().orElseThrow();

        assertThat(summary.released()).isEqualTo(2);
        assertThat(summary.averagePercentCorrect()).isEqualTo(50.0);
        assertThat(summary.hardestQuestionOrder()).isEqualTo(2);
    }

    /** A released one-mark MCQ with options A-D. */
    private Question mcq(Long sessionId, int order, int correct) {
        Question q = bank.add(sessionId, order, QuestionType.MCQ, "Q" + order, TestQuiz.OPTIONS, correct, 60);
        engine.release(sessionId, q.getId());
        return q;
    }

    /** Stores an MCQ answer scored against the key. */
    private void answer(QuizSession session, Question q, String rollNo, int option, boolean onTime) {
        Response r = new Response(session, q, rollNo);
        r.setSubmittedAt(Instant.now());
        r.setOnTime(onTime);
        r.setSelectedOptionIndex(option);
        r.setCorrect(option == q.getCorrectOptionIndex());
        responses.save(r);
    }
}
