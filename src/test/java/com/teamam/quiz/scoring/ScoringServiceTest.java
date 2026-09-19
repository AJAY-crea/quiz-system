package com.teamam.quiz.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.questionbank.RandomisationService;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.session.SessionEngine;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ScoringServiceTest {

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private RandomisationService randomisationService;

    @Autowired
    private SessionEngine sessionEngine;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void scoresMcqAgainstShuffledAnswerKey() {
        QuizSession session = sessionEngine.createSession("Scoring test session");
        Question question = new Question(session, 1, QuestionType.MCQ, "Capital of India?");
        question.setOptions(List.of("Mumbai", "Delhi", "Chennai", "Kolkata"));
        question.setCorrectOptionIndex(1); // "Delhi"
        question = questionRepository.save(question);

        String rollNo = "CS23B001";
        List<String> shuffled = randomisationService.shuffleFor(rollNo, question);
        int correctShuffledIndex = shuffled.indexOf("Delhi");
        int wrongShuffledIndex = (correctShuffledIndex + 1) % shuffled.size();

        assertThat(scoringService.scoreMcq(rollNo, question, correctShuffledIndex)).isTrue();
        assertThat(scoringService.scoreMcq(rollNo, question, wrongShuffledIndex)).isFalse();
    }

    @Test
    void rejectsScoringATypedQuestionAsMcq() {
        QuizSession session = sessionEngine.createSession("Scoring test session 2");
        Question typedQuestion = questionRepository.save(
                new Question(session, 1, QuestionType.TYPED, "Explain Ohm's law."));

        assertThatThrownBy(() -> scoringService.scoreMcq("CS23B001", typedQuestion, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
