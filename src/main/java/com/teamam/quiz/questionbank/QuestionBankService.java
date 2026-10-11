package com.teamam.quiz.questionbank;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Module 5 (Ajay): stores the questions for a session. */
@Service
public class QuestionBankService {

    private final QuizSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;

    public QuestionBankService(QuizSessionRepository sessionRepository, QuestionRepository questionRepository) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
    }

    /** Adds a question; an MCQ must have at least 2 options and a correct option that exists. */
    @Transactional
    public Question add(
            Long sessionId,
            int orderIndex,
            QuestionType type,
            String text,
            List<String> options,
            Integer correctOptionIndex,
            int durationSeconds) {
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));

        if (type == QuestionType.MCQ) {
            if (options == null || options.size() < 2) {
                throw new IllegalArgumentException("an MCQ question needs at least 2 options");
            }
            if (correctOptionIndex == null || correctOptionIndex < 0 || correctOptionIndex >= options.size()) {
                throw new IllegalArgumentException("correctOptionIndex must point at one of the given options");
            }
        }

        Question question = new Question(session, orderIndex, type, text);
        if (options != null) {
            question.setOptions(options);
        }
        question.setCorrectOptionIndex(correctOptionIndex);
        question.setDurationSeconds(durationSeconds);
        return questionRepository.save(question);
    }
}
