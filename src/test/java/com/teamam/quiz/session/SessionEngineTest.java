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

    // Guards against: ending the lecture cutting off students mid-answer, or an accidental End being
    // impossible to undo.
    @Test
    void endIsRefusedWhileAQuestionIsOpenAndReleasingReopens() {
        QuizSession session = sessionEngine.createSession("end");
        Question open = new Question(session, 1, QuestionType.TYPED, "open");
        open.setDurationSeconds(60);
        questionRepository.save(open);
        Question over = new Question(session, 2, QuestionType.TYPED, "over");
        over.setDurationSeconds(0);
        questionRepository.save(over);
        Question later = new Question(session, 3, QuestionType.TYPED, "later");
        later.setDurationSeconds(60);
        questionRepository.save(later);

        sessionEngine.release(session.getId(), open.getId());
        assertThatThrownBy(() -> sessionEngine.end(session.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still open");

        sessionEngine.release(session.getId(), over.getId());
        sessionEngine.end(session.getId());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ENDED);

        sessionEngine.release(session.getId(), later.getId());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);
    }
}
