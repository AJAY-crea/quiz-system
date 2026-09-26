package com.teamam.quiz;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.resilience.AnswerSubmission;
import com.teamam.quiz.roster.JoinWindowService;
import com.teamam.quiz.roster.RosterService;
import com.teamam.quiz.session.SessionEngine;
import java.util.List;
import org.springframework.context.ApplicationContext;

/** Test fixture: a fresh session with one released MCQ and the given students joined. */
public final class TestQuiz {

    public static final List<String> OPTIONS = List.of("A", "B", "C", "D");
    public static final int CORRECT_INDEX = 2;

    public final Long sessionId;
    public final Long questionId;

    private TestQuiz(Long sessionId, Long questionId) {
        this.sessionId = sessionId;
        this.questionId = questionId;
    }

    public static String deviceOf(String rollNo) {
        return "dev-" + rollNo;
    }

    public static TestQuiz startWithReleasedMcq(ApplicationContext ctx, int durationSeconds, String... rollNos) {
        SessionEngine engine = ctx.getBean(SessionEngine.class);
        RosterService roster = ctx.getBean(RosterService.class);
        QuestionBankService bank = ctx.getBean(QuestionBankService.class);

        QuizSession session = engine.createSession("test session");
        roster.uploadRoster(session.getId(), List.of(rollNos));
        String ticket = joinTicket(ctx, session.getId());
        for (String rollNo : rollNos) {
            roster.join(session.getId(), rollNo, deviceOf(rollNo), ticket);
        }
        Question question = bank.add(
                session.getId(), 1, QuestionType.MCQ, "Pick C", OPTIONS, CORRECT_INDEX, durationSeconds);
        engine.release(session.getId(), question.getId());
        return new TestQuiz(session.getId(), question.getId());
    }

    /** Opens joining for a minute and exchanges the current QR code for a join ticket, as a phone would. */
    public static String joinTicket(ApplicationContext ctx, Long sessionId) {
        JoinWindowService joinWindow = ctx.getBean(JoinWindowService.class);
        joinWindow.open(sessionId, 60);
        return joinWindow.issueTicket(sessionId, joinWindow.status(sessionId).code()).ticket();
    }

    public AnswerSubmission answer(String submissionId, String rollNo, int chosenShuffledIndex) {
        return new AnswerSubmission(submissionId, rollNo, deviceOf(rollNo), questionId, chosenShuffledIndex, null);
    }
}
