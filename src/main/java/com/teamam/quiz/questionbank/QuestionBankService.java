package com.teamam.quiz.questionbank;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Module 5 (Ajay): stores the questions for a session. */
@Service
public class QuestionBankService {

    private final QuizSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final ResponseRepository responseRepository;

    public QuestionBankService(
            QuizSessionRepository sessionRepository,
            QuestionRepository questionRepository,
            ResponseRepository responseRepository) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
    }

    public static final int MAX_MARKS_LIMIT = 100;

    /** Adds a question worth 1 mark. */
    @Transactional
    public Question add(
            Long sessionId,
            int orderIndex,
            QuestionType type,
            String text,
            List<String> options,
            Integer correctOptionIndex,
            int durationSeconds) {
        return add(sessionId, orderIndex, type, text, options, correctOptionIndex, durationSeconds, 1);
    }

    /** Adds a question with at most one correct option (MCQ or typed). */
    @Transactional
    public Question add(
            Long sessionId,
            int orderIndex,
            QuestionType type,
            String text,
            List<String> options,
            Integer correctOptionIndex,
            int durationSeconds,
            int maxMarks) {
        return addQuestion(sessionId, orderIndex, type, text, options,
                correctOptionIndex == null ? List.of() : List.of(correctOptionIndex), durationSeconds, maxMarks);
    }

    /**
     * Adds a question. A choice question needs at least 2 options and correct options that exist:
     * exactly one for MCQ, one or more for MULTI.
     */
    @Transactional
    public Question addQuestion(
            Long sessionId,
            int orderIndex,
            QuestionType type,
            String text,
            List<String> options,
            List<Integer> correctOptionIndexes,
            int durationSeconds,
            int maxMarks) {
        if (maxMarks < 1 || maxMarks > MAX_MARKS_LIMIT) {
            throw new IllegalArgumentException("a question must be worth between 1 and " + MAX_MARKS_LIMIT + " marks");
        }
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));

        if (type.isChoice()) {
            if (options == null || options.size() < 2) {
                throw new IllegalArgumentException("a multiple-choice question needs at least 2 options");
            }
            requireValidKey(type, options.size(), correctOptionIndexes);
        }

        Question question = new Question(session, orderIndex, type, text);
        if (options != null) {
            question.setOptions(options);
        }
        question.setCorrectOptionIndexes(type.isChoice() ? correctOptionIndexes : List.of());
        question.setDurationSeconds(durationSeconds);
        question.setMaxMarks(maxMarks);
        return questionRepository.save(question);
    }

    /** Adds a whole file of questions after the existing ones. All or nothing: one bad line adds none. */
    @Transactional
    public int addAll(Long sessionId, List<QuestionFileParser.ParsedQuestion> parsed) {
        int order = questionRepository.findBySessionIdOrderByOrderIndex(sessionId).size();
        for (QuestionFileParser.ParsedQuestion q : parsed) {
            order++;
            try {
                addQuestion(sessionId, order, q.type(), q.text(), q.options(), q.correctOptionIndexes(),
                        q.durationSeconds(), q.maxMarks());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Question \"" + q.text() + "\": " + e.getMessage());
            }
        }
        return parsed.size();
    }

    /** Removes a question that hasn't been released (e.g. a typo) and closes the gap in the numbering. */
    @Transactional
    public void remove(Long sessionId, Long questionId) {
        Question question = questionRepository
                .findById(questionId)
                .filter(q -> q.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new NoSuchElementException("question not found: " + questionId));
        if (question.getReleasedAt() != null) {
            throw new IllegalStateException("question " + question.getOrderIndex() + " has been released and can't be removed");
        }
        int removedOrder = question.getOrderIndex();
        questionRepository.delete(question);
        questionRepository.flush();
        for (Question later : questionRepository.findBySessionIdOrderByOrderIndex(sessionId)) {
            if (later.getOrderIndex() > removedOrder) {
                later.setOrderIndex(later.getOrderIndex() - 1);
            }
        }
    }

    /** Fixes a wrong answer key for a one-answer MCQ. */
    @Transactional
    public void changeAnswerKey(Long sessionId, Long questionId, int correctOptionIndex) {
        changeAnswerKey(sessionId, questionId, List.of(correctOptionIndex));
    }

    /** Fixes a wrong answer key, even after the quiz: every stored answer to the question is re-scored. */
    @Transactional
    public void changeAnswerKey(Long sessionId, Long questionId, List<Integer> correctOptionIndexes) {
        Question question = questionRepository
                .findById(questionId)
                .filter(q -> q.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new NoSuchElementException("question not found: " + questionId));
        if (!question.getType().isChoice()) {
            throw new IllegalArgumentException("only multiple-choice questions have an answer key");
        }
        requireValidKey(question.getType(), question.getOptions().size(), correctOptionIndexes);
        question.setCorrectOptionIndexes(correctOptionIndexes);
        questionRepository.save(question);
        List<Integer> key = question.getCorrectOptionIndexes();
        List<Response> answers = responseRepository.findBySessionIdAndQuestionId(sessionId, questionId);
        answers.forEach(r -> r.setCorrect(r.getSelectedOptions().equals(key)));
        responseRepository.saveAll(answers);
    }

    /** The key must name existing options, without repeats: exactly one for MCQ, at least one for MULTI. */
    private static void requireValidKey(QuestionType type, int optionCount, List<Integer> key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("choose the correct option");
        }
        if (type == QuestionType.MCQ && key.size() != 1) {
            throw new IllegalArgumentException("a one-answer question has exactly one correct option; use \"several correct answers\" for more");
        }
        if (key.stream().distinct().count() != key.size()) {
            throw new IllegalArgumentException("the same correct option is listed twice");
        }
        if (key.stream().anyMatch(i -> i == null || i < 0 || i >= optionCount)) {
            throw new IllegalArgumentException("correctOptionIndex must point at one of the given options");
        }
    }
}
