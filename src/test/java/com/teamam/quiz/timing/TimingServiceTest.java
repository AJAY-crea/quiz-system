package com.teamam.quiz.timing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // Guards against: closing early cutting students off with no notice, or being repeatable so the
    // deadline keeps moving.
    @Test
    void closeSoonMovesTheDeadlineToAFewSecondsFromNow() {
        QuizSession session = sessionEngine.createSession("Close early session");
        Question question = questionRepository.save(new Question(session, 1, QuestionType.TYPED, "Explain"));
        question.setDurationSeconds(600);
        questionRepository.save(question);
        sessionEngine.release(session.getId(), question.getId());

        Instant before = Instant.now();
        Instant deadline = timingService.closeSoon(question.getId());

        assertThat(deadline).isAfterOrEqualTo(before.plus(TimingService.CLOSE_NOTICE));
        assertThat(deadline).isBefore(before.plus(TimingService.CLOSE_NOTICE).plusSeconds(3));
        assertThat(timingService.deadlineFor(question.getId())).isEqualTo(deadline);
        assertThatThrownBy(() -> timingService.closeSoon(question.getId())).isInstanceOf(IllegalStateException.class);
    }

    // Guards against: the instructor's extension not actually moving the server-side cutoff.
    @Test
    void extendDeadlineMovesCutoff() {
        QuizSession session = sessionEngine.createSession("Extension test session");
        Question question = questionRepository.save(
                new Question(session, 1, QuestionType.MCQ, "3 + 3 = ?"));
        question.setDurationSeconds(10);
        questionRepository.save(question);
        sessionEngine.release(session.getId(), question.getId());

        Instant original = timingService.deadlineFor(question.getId());
        Instant extended = timingService.extendDeadline(question.getId(), 20);

        assertThat(extended).isEqualTo(original.plusSeconds(20));
        assertThat(timingService.deadlineFor(question.getId())).isEqualTo(extended);
        assertThat(timingService.isOnTime(question.getId(), original.plusSeconds(15))).isTrue();
    }
}
