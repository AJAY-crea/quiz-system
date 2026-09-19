package com.teamam.quiz.timing;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.session.SessionEngine;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TimingServiceTest {

    @Autowired
    private TimingService timingService;

    @Autowired
    private SessionEngine sessionEngine;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void recordsLateSubmissionAsTimeout() {
        QuizSession session = sessionEngine.createSession("Timing test session");
        Question question = questionRepository.save(
                new Question(session, 1, QuestionType.MCQ, "2 + 2 = ?"));
        question.setDurationSeconds(10);
        questionRepository.save(question);

        sessionEngine.release(session.getId(), question.getId());

        Instant deadline = timingService.deadlineFor(question.getId());

        assertThat(timingService.isOnTime(question.getId(), deadline)).isTrue();
        assertThat(timingService.isOnTime(question.getId(), deadline.plusMillis(200))).isTrue();
        assertThat(timingService.isOnTime(question.getId(), deadline.plusSeconds(2))).isFalse();
    }
}
