package com.teamam.quiz.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamam.quiz.QuizSystemApplication;
import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.SessionStatus;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.session.SessionStateService;
import com.teamam.quiz.session.SessionStateView;
import com.teamam.quiz.timing.TimingService;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * A real restart: two application contexts started one after the other against the same
 * file-backed H2 database, the way the instructor's laptop would reopen the JAR.
 */
class RestartRecoveryTest {

    @TempDir
    Path dataDir;

    // Guards against: an instructor-app restart losing the open question, its deadline, or saved answers.
    @Test
    void resumesSessionAfterRestart() {
        String rollNo = "CS23B201";
        TestQuiz quiz;
        Instant deadlineBeforeRestart;
        AnswerSubmission submission;

        try (ConfigurableApplicationContext beforeRestart = boot()) {
            quiz = TestQuiz.startWithReleasedMcq(beforeRestart, 120, rollNo);
            deadlineBeforeRestart = beforeRestart.getBean(TimingService.class).deadlineFor(quiz.questionId);
            submission = quiz.answer("sub-r1", rollNo, 0);
            assertThat(beforeRestart.getBean(ResilienceService.class).accept(quiz.sessionId, submission).outcome())
                    .isEqualTo(AcceptOutcome.ACCEPTED);
        }

        try (ConfigurableApplicationContext afterRestart = boot()) {
            QuizSession session = afterRestart.getBean(QuizSessionRepository.class)
                    .findById(quiz.sessionId)
                    .orElseThrow();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);
            assertThat(session.getCurrentQuestionOrder()).isEqualTo(1);
            assertThat(afterRestart.getBean(TimingService.class).deadlineFor(quiz.questionId))
                    .isEqualTo(deadlineBeforeRestart);

            AcceptResult resend = afterRestart.getBean(ResilienceService.class).accept(quiz.sessionId, submission);
            assertThat(resend.outcome()).isEqualTo(AcceptOutcome.DUPLICATE);
            assertThat(resend.sameSubmission()).isTrue();

            SessionStateView state = afterRestart.getBean(SessionStateService.class)
                    .currentState(quiz.sessionId, rollNo, TestQuiz.deviceOf(rollNo));
            assertThat(state.currentQuestion().id()).isEqualTo(quiz.questionId);
            assertThat(state.alreadyAnswered()).isTrue();
            assertThat(state.remainingMs()).isPositive();
        }
    }

    private ConfigurableApplicationContext boot() {
        String dbPath = dataDir.resolve("quizdb").toAbsolutePath().toString().replace('\\', '/');
        // Command-line args, because they override the in-memory settings in the test application.yml.
        return new SpringApplicationBuilder(QuizSystemApplication.class)
                .run(
                        "--spring.datasource.url=jdbc:h2:file:" + dbPath,
                        "--spring.jpa.hibernate.ddl-auto=update",
                        "--server.port=0");
    }
}
