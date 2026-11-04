package com.teamam.quiz.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamam.quiz.analytics.AnalyticsService;
import com.teamam.quiz.analytics.QuestionAnalytics;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.questionbank.QuestionFileParser;
import com.teamam.quiz.questionbank.RandomisationService;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.session.SessionEngine;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SeveralCorrectAnswersTest {

    private static final List<String> NUMBERS = List.of("2", "4", "5", "9");

    @Autowired
    private QuestionBankService bank;

    @Autowired
    private ScoringService scoring;

    @Autowired
    private RandomisationService randomisation;

    @Autowired
    private SessionEngine engine;

    @Autowired
    private ResponseRepository responses;

    @Autowired
    private AnalyticsService analytics;

    // Guards against: half-right answers getting full marks. Only the exact set counts, in the
    // student's own shuffled order.
    @Test
    void onlyTheExactSetIsCorrect() {
        Question primes = primesQuestion(engine.createSession("multi"));
        String roll = "CS23B601";
        List<String> mine = randomisation.shuffleFor(roll, primes);
        int two = mine.indexOf("2");
        int four = mine.indexOf("4");
        int five = mine.indexOf("5");

        assertThat(scoring.scoreChoices(roll, primes, List.of(five, two))).isTrue();
        assertThat(scoring.scoreChoices(roll, primes, List.of(two))).isFalse();             // too few
        assertThat(scoring.scoreChoices(roll, primes, List.of(two, five, four))).isFalse(); // too many
        assertThat(scoring.remapAll(roll, primes, List.of(five, two, two))).containsExactly(0, 2);
    }

    // Guards against: a several-answer question with no key, a key that names a missing option, or a
    // one-answer question quietly given two keys.
    @Test
    void refusesBadKeys() {
        Long sid = engine.createSession("bad keys").getId();
        assertThatThrownBy(() -> bank.addQuestion(sid, 1, QuestionType.MULTI, "q", NUMBERS, List.of(), 60, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bank.addQuestion(sid, 1, QuestionType.MULTI, "q", NUMBERS, List.of(0, 7), 60, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bank.addQuestion(sid, 1, QuestionType.MULTI, "q", NUMBERS, List.of(0, 0), 60, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bank.addQuestion(sid, 1, QuestionType.MCQ, "q", NUMBERS, List.of(0, 2), 60, 1))
                .hasMessageContaining("several correct answers");
    }

    // Guards against: the option counts and the key fix only understanding one correct option.
    @Test
    void analyticsCountEveryTickAndKeyFixRescores() {
        QuizSession session = engine.createSession("multi analytics");
        Question primes = primesQuestion(session);
        tick(session, primes, "A", List.of(0, 2));     // right
        tick(session, primes, "B", List.of(0));        // too few
        tick(session, primes, "C", List.of(0, 1, 2));  // too many

        QuestionAnalytics result = analytics.forQuestion(primes.getId());
        assertThat(result.percentCorrect()).isEqualTo(33.3);
        assertThat(result.options()).extracting(QuestionAnalytics.OptionStat::count).containsExactly(3L, 1L, 2L, 0L);
        assertThat(result.options()).extracting(QuestionAnalytics.OptionStat::correct)
                .containsExactly(true, false, true, false);

        bank.changeAnswerKey(session.getId(), primes.getId(), List.of(0));

        assertThat(analytics.forQuestion(primes.getId()).percentCorrect()).isEqualTo(33.3);
        assertThat(responses.findBySessionIdAndQuestionIdAndRollNo(session.getId(), primes.getId(), "B")
                .orElseThrow().getCorrect()).isTrue();
    }

    // Guards against: the CSV format for several answers being misread.
    @Test
    void csvReadsSeveralCorrectOptions() throws Exception {
        List<QuestionFileParser.ParsedQuestion> parsed = QuestionFileParser.parse(new ByteArrayInputStream(
                "MULTI,60,2,\"Which are prime?\",1;3,2,4,5\n".getBytes(StandardCharsets.UTF_8)));
        assertThat(parsed.get(0).type()).isEqualTo(QuestionType.MULTI);
        assertThat(parsed.get(0).correctOptionIndexes()).containsExactly(0, 2);
        assertThat(parsed.get(0).options()).containsExactly("2", "4", "5");
    }

    /** "Which are prime?" with options 2, 4, 5, 9: correct = 2 and 5. Released. */
    private Question primesQuestion(QuizSession session) {
        Question q = bank.addQuestion(session.getId(), 1, QuestionType.MULTI, "Which are prime?", NUMBERS, List.of(2, 0), 60, 2);
        engine.release(session.getId(), q.getId());
        return q;
    }

    /** Stores an on-time answer with these real option numbers ticked, scored against the key. */
    private void tick(QuizSession session, Question q, String roll, List<Integer> options) {
        Response r = new Response(session, q, roll);
        r.setSubmittedAt(Instant.now());
        r.setSelectedOptions(options);
        r.setCorrect(r.getSelectedOptions().equals(q.getCorrectOptionIndexes()));
        responses.save(r);
    }
}
