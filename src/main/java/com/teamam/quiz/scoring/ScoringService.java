package com.teamam.quiz.scoring;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.questionbank.RandomisationService;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module 6: MCQ auto-scoring plus typed-answer review. An MCQ's chosen index is in the student's
 * own shuffled view, so it is remapped back to the canonical option (the same permutation
 * Randomisation handed them) before comparing against the answer key. Typed answers are never
 * auto-marked: the instructor marks them afterwards, one group of identical answers at a time.
 */
@Service
public class ScoringService {

    private final RandomisationService randomisationService;
    private final QuestionRepository questionRepository;
    private final ResponseRepository responseRepository;

    public ScoringService(
            RandomisationService randomisationService,
            QuestionRepository questionRepository,
            ResponseRepository responseRepository) {
        this.randomisationService = randomisationService;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
    }

    /** Correct if the student's tap, un-shuffled back to the real option, equals the answer key. */
    public boolean scoreMcq(String rollNo, Question question, int chosenShuffledIndex) {
        if (question.getType() != QuestionType.MCQ) {
            throw new IllegalArgumentException("scoreMcq called on a non-MCQ question: " + question.getId());
        }
        int canonicalIndex = randomisationService.remap(rollNo, question, chosenShuffledIndex);
        return Objects.equals(canonicalIndex, question.getCorrectOptionIndex());
    }

    /** Correct only if the student picked exactly the right options: no more and no fewer. */
    public boolean scoreChoices(String rollNo, Question question, List<Integer> chosenShuffledIndexes) {
        if (!question.getType().isChoice()) {
            throw new IllegalArgumentException("scoreChoices called on a typed question: " + question.getId());
        }
        return remapAll(rollNo, question, chosenShuffledIndexes).equals(question.getCorrectOptionIndexes());
    }

    /** The student's picks, un-shuffled to the real option numbers, sorted and without repeats. */
    public List<Integer> remapAll(String rollNo, Question question, List<Integer> chosenShuffledIndexes) {
        return chosenShuffledIndexes.stream()
                .map(i -> randomisationService.remap(rollNo, question, i))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The one rule for what an answer earns, used by the score sheet and analytics alike. Null means
     * "no mark yet": not answered, or a typed answer still waiting for the instructor. Late scores 0.
     */
    public static Double marksFor(Question question, Response response) {
        if (response == null) {
            return null;
        }
        if (!response.isOnTime()) {
            return 0.0;
        }
        if (question.getType() == QuestionType.TYPED) {
            return response.getAwardedMarks();
        }
        return Boolean.TRUE.equals(response.getCorrect()) ? (double) question.getMaxMarks() : 0.0;
    }

    /** Is this answer waiting for the instructor to mark it? */
    public static boolean awaitsMarking(Question question, Response response) {
        return question.getType() == QuestionType.TYPED && response.isOnTime() && response.getAwardedMarks() == null;
    }

    /**
     * Two answers are "the same" if they differ only in case, spacing around symbols or a final full
     * stop ("V = IR." and "v=ir"). Spaces between words or digits stay: "1 2" and "12" are different
     * answers, and one mark would be applied to the whole group.
     */
    static String normalise(String answer) {
        return Normalizer.normalize(answer, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll(" ?([^\\p{L}\\p{N} ]) ?", "$1")
                .replaceAll("[.,;:!?]+$", "");
    }

    /** Everything needed to mark one typed question: answers grouped, most common wording first. */
    @Transactional(readOnly = true)
    public TypedReview groupTyped(Long sessionId, Long questionId) {
        Question question = requireTypedQuestion(sessionId, questionId);
        List<Response> responses = responseRepository.findBySessionIdAndQuestionId(sessionId, questionId).stream()
                .sorted(Comparator.comparing(Response::getRollNo))
                .toList();

        Map<String, List<Response>> byWording = new LinkedHashMap<>();
        for (Response r : responses) {
            if (r.isOnTime()) {
                byWording.computeIfAbsent(normalise(r.getTypedAnswer()), key -> new java.util.ArrayList<>()).add(r);
            }
        }
        List<TypedReview.Group> groups = byWording.entrySet().stream()
                .map(entry -> group(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(TypedReview.Group::count).reversed()
                        .thenComparing(TypedReview.Group::key))
                .toList();
        List<TypedReview.Answer> late = responses.stream().filter(r -> !r.isOnTime()).map(ScoringService::answer).toList();

        long pending = responses.stream().filter(r -> awaitsMarking(question, r)).count();
        long onTime = responses.size() - late.size();
        return new TypedReview(
                question.getId(), question.getOrderIndex(), question.getText(), question.getMaxMarks(),
                onTime - pending, pending, groups, late);
    }

    /** Gives every on-time answer with this wording the same mark; null clears it. Returns how many changed. */
    @Transactional
    public int markGroup(Long sessionId, Long questionId, String key, Double marks) {
        Question question = requireTypedQuestion(sessionId, questionId);
        requireValidMark(question, marks);
        List<Response> group = responseRepository.findBySessionIdAndQuestionId(sessionId, questionId).stream()
                .filter(Response::isOnTime)
                .filter(r -> normalise(r.getTypedAnswer()).equals(key))
                .toList();
        if (group.isEmpty()) {
            throw new NoSuchElementException("no answers with that wording");
        }
        group.forEach(r -> r.setAwardedMarks(marks));
        responseRepository.saveAll(group);
        return group.size();
    }

    /** Marks one student's answer on its own (an override inside a group); null clears it. */
    @Transactional
    public void markOne(Long sessionId, Long responseId, Double marks) {
        Response response = responseRepository
                .findById(responseId)
                .filter(r -> r.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new NoSuchElementException("answer not found: " + responseId));
        Question question = response.getQuestion();
        if (question.getType() != QuestionType.TYPED) {
            throw new IllegalArgumentException("only typed answers are marked by hand");
        }
        if (!response.isOnTime()) {
            throw new IllegalStateException("late answers score 0 and can't be marked");
        }
        requireValidMark(question, marks);
        response.setAwardedMarks(marks);
        responseRepository.save(response);
    }

    /** Builds one group; its shared mark is shown only if every answer in it has that same mark. */
    private static TypedReview.Group group(String key, List<Response> answers) {
        long distinctMarks = answers.stream().map(Response::getAwardedMarks).distinct().count();
        return new TypedReview.Group(
                key,
                answers.get(0).getTypedAnswer(),
                answers.size(),
                distinctMarks == 1 ? answers.get(0).getAwardedMarks() : null,
                distinctMarks > 1,
                answers.stream().map(ScoringService::answer).toList());
    }

    /** Copies a stored answer into the marking view. */
    private static TypedReview.Answer answer(Response r) {
        return new TypedReview.Answer(r.getId(), r.getRollNo(), r.getTypedAnswer(), r.getAwardedMarks());
    }

    /** Loads the question, checking it belongs to this session and is a typed one. */
    private Question requireTypedQuestion(Long sessionId, Long questionId) {
        Question question = questionRepository
                .findById(questionId)
                .filter(q -> q.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new NoSuchElementException("question not found: " + questionId));
        if (question.getType() != QuestionType.TYPED) {
            throw new IllegalArgumentException("only typed answers are marked by hand");
        }
        return question;
    }

    /** A mark must be between 0 and the question's maximum (null = clear it). */
    private static void requireValidMark(Question question, Double marks) {
        if (marks != null && !(marks >= 0 && marks <= question.getMaxMarks())) {
            throw new IllegalArgumentException("marks must be between 0 and " + question.getMaxMarks());
        }
    }
}
