package com.teamam.quiz.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.SessionStatus;
import com.teamam.quiz.repository.QuestionRepository;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SessionEngineTest {

    @Autowired
    private SessionEngine sessionEngine;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void refusesEarlyRequestForNextQuestion() {
        QuizSession session = sessionEngine.createSession("5-question set");
        List<Question> questions = IntStream.rangeClosed(1, 5)
                .mapToObj(order -> questionRepository.save(
                        new Question(session, order, QuestionType.MCQ, "Question " + order)))
                .toList();

        Question released = sessionEngine.release(session.getId(), questions.get(0).getId());
        assertThat(released.getId()).isEqualTo(questions.get(0).getId());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);

        Question thirdQuestion = questions.get(2);
        assertThatThrownBy(() -> sessionEngine.release(session.getId(), thirdQuestion.getId()))
                .isInstanceOf(IllegalStateException.class);
    }
}
