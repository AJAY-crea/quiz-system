package com.teamam.quiz.session;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.SessionStatus;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import java.util.NoSuchElementException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionEngine {

    private final QuizSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public SessionEngine(
            QuizSessionRepository sessionRepository,
            QuestionRepository questionRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.messagingTemplate = messagingTemplate;
    }

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

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId + "/question", QuestionView.of(question));
        return question;
    }

    private QuizSession requireSession(Long sessionId) {
        return sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
    }
}
