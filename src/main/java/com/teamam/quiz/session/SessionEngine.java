package com.teamam.quiz.session;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.SessionStatus;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.timing.TimingService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Module 2 (Mukesh): creates sessions and releases questions one at a time, in order. */
@Service
public class SessionEngine {

    private final QuizSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TimingService timingService;

    public SessionEngine(
            QuizSessionRepository sessionRepository,
            QuestionRepository questionRepository,
            SimpMessagingTemplate messagingTemplate,
            TimingService timingService) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.messagingTemplate = messagingTemplate;
        this.timingService = timingService;
    }

    /** Creates a new session (one lecture's quiz). */
    @Transactional
    public QuizSession createSession(String title) {
        return sessionRepository.save(new QuizSession(title));
    }

    /**
     * Releases the next question to every connected client. Refuses to release out of order:
     * the question's orderIndex must be exactly one past the last released question.
     */
    @Transactional
    public Question release(Long sessionId, Long questionId) {
        QuizSession session = requireSession(sessionId);
        Question question = questionRepository
                .findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("question not found: " + questionId));

        if (!question.getSession().getId().equals(sessionId)) {
            throw new IllegalArgumentException(
                    "question " + questionId + " does not belong to session " + sessionId);
        }

        int nextOrder = session.getCurrentQuestionOrder() + 1;
        if (question.getOrderIndex() != nextOrder) {
            throw new IllegalStateException(
                    "cannot release question at order "
                            + question.getOrderIndex()
                            + "; next expected release order is "
                            + nextOrder);
        }

        session.setCurrentQuestionOrder(question.getOrderIndex());
        session.setStatus(SessionStatus.RUNNING);
        sessionRepository.save(session);

        // Millisecond precision so the deadline read back after a restart equals the one computed before it.
        question.setReleasedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        questionRepository.save(question);

        announce(sessionId, new SessionEvent(SessionEvent.RELEASED, question.getId()));
        return question;
    }

    /**
     * Ends the lecture: phones show "the quiz has ended". Refused while a question is open, so nobody
     * is cut off mid-answer. Releasing another question later simply makes the session RUNNING again.
     */
    @Transactional
    public void end(Long sessionId) {
        QuizSession session = requireSession(sessionId);
        if (session.getCurrentQuestionOrder() > 0) {
            Question current = questionRepository
                    .findBySessionIdAndOrderIndex(sessionId, session.getCurrentQuestionOrder())
                    .orElseThrow(() -> new IllegalStateException("current question missing for session " + sessionId));
            if (Instant.now().isBefore(timingService.deadlineFor(current.getId()))) {
                throw new IllegalStateException(
                        "Question " + current.getOrderIndex() + " is still open. Close it first, or wait for its time to end.");
            }
        }
        session.setStatus(SessionStatus.ENDED);
        sessionRepository.save(session);
        announce(sessionId, new SessionEvent(SessionEvent.ENDED, null));
    }

    /** Tells every connected browser to refetch its state (e.g. after a deadline extension). */
    public void announce(Long sessionId, SessionEvent event) {
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/question", event);
    }

    /** Loads the session or fails with "not found". */
    private QuizSession requireSession(Long sessionId) {
        return sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
    }
}
