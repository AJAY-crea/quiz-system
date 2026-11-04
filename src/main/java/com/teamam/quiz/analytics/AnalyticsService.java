package com.teamam.quiz.analytics;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.scoring.ScoringService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module 10: per-question analytics, kept for the whole semester because sessions stay in the database.
 * Discrimination index = (average score on this question of the top 27% of students by total)
 * minus (the same for the bottom 27%). Near +1: the question separates strong from weak students.
 * Near 0: it doesn't. Negative: weak students did better, which usually means a wrong answer key.
 * Only students who answered something are ranked, so absentees don't fill the bottom group.
 */
@Service
public class AnalyticsService {

    private static final double GROUP_SHARE = 0.27;

    /** Below this, the top and bottom groups would be one or two students: meaningless. */
    private static final int MIN_STUDENTS_FOR_DISCRIMINATION = 4;

    private static final double HARD_BELOW_PERCENT = 30;
    private static final double WEAK_DISCRIMINATION = 0.2;

    private final QuizSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final ResponseRepository responseRepository;
    private final ParticipantRepository participantRepository;

    public AnalyticsService(
            QuizSessionRepository sessionRepository,
            QuestionRepository questionRepository,
            ResponseRepository responseRepository,
            ParticipantRepository participantRepository) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
        this.participantRepository = participantRepository;
    }

    /** Analytics for one question. */
    @Transactional(readOnly = true)
    public QuestionAnalytics forQuestion(Long questionId) {
        Question question = questionRepository
                .findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("question not found: " + questionId));
        return forSession(question.getSession().getId()).stream()
                .filter(a -> a.questionId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("question has not been released yet"));
    }

    /** Analytics for every released question of a session, in order. */
    @Transactional(readOnly = true)
    public List<QuestionAnalytics> forSession(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new NoSuchElementException("session not found: " + sessionId);
        }
        List<Question> released = questionRepository.findBySessionIdOrderByOrderIndex(sessionId).stream()
                .filter(q -> q.getReleasedAt() != null)
                .toList();
        Map<Long, Question> questionsById = new HashMap<>();
        released.forEach(q -> questionsById.put(q.getId(), q));

        // Each student's total, and their answers by question. Marks still to be given count as 0.
        Map<String, Double> totals = new HashMap<>();
        Map<String, Response> answers = new HashMap<>();
        for (Response r : responseRepository.findBySessionId(sessionId)) {
            Question question = questionsById.get(r.getQuestion().getId());
            if (question != null) {
                answers.put(r.getRollNo() + "#" + question.getId(), r);
                Double marks = ScoringService.marksFor(question, r);
                totals.merge(r.getRollNo(), marks == null ? 0.0 : marks, Double::sum);
            }
        }
        // Ties are broken by roll number, so the same data always gives the same groups.
        List<String> ranked = totals.keySet().stream()
                .sorted(Comparator.comparing((String roll) -> totals.get(roll)).reversed()
                        .thenComparing(Function.identity()))
                .toList();

        return released.stream().map(q -> analyse(q, ranked, answers)).toList();
    }

    /** One row per session, oldest first: how the class did across the semester. */
    @Transactional(readOnly = true)
    public List<SessionSummary> semester() {
        return sessionRepository.findAll().stream()
                .sorted(Comparator.comparing(QuizSession::getId))
                .map(this::summarise)
                .toList();
    }

    /** Summarises one session for the semester view. */
    private SessionSummary summarise(QuizSession session) {
        List<QuestionAnalytics> questions = forSession(session.getId());
        List<QuestionAnalytics> answered = questions.stream().filter(q -> q.percentCorrect() != null).toList();
        QuestionAnalytics hardest = answered.stream()
                .min(Comparator.comparing(QuestionAnalytics::percentCorrect))
                .orElse(null);
        return new SessionSummary(
                session.getId(), session.getTitle(), session.getCreatedAt(), session.getRosterRollNumbers().size(),
                participantRepository.findBySessionId(session.getId()).size(), questions.size(),
                answered.isEmpty() ? null : round(answered.stream().mapToDouble(QuestionAnalytics::percentCorrect).average().orElse(0)),
                hardest == null ? null : hardest.orderIndex(),
                hardest == null ? null : hardest.percentCorrect());
    }

    /** Works out all the figures for one question. */
    private static QuestionAnalytics analyse(Question q, List<String> ranked, Map<String, Response> answers) {
        List<Response> received = ranked.stream()
                .map(roll -> answers.get(roll + "#" + q.getId()))
                .filter(Objects::nonNull)
                .toList();
        List<Response> onTime = received.stream().filter(Response::isOnTime).toList();
        long awaiting = received.stream().filter(r -> ScoringService.awaitsMarking(q, r)).count();

        List<Double> marked = onTime.stream()
                .map(r -> ScoringService.marksFor(q, r))
                .filter(Objects::nonNull)
                .map(marks -> marks / q.getMaxMarks())
                .toList();
        Double percentCorrect = marked.isEmpty()
                ? null
                : round(marked.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100);
        Double discrimination = awaiting > 0 || received.isEmpty() ? null : discrimination(q, ranked, answers);
        List<QuestionAnalytics.OptionStat> options = optionStats(q, onTime);
        // With several correct options, "more chose X than the correct option" has no single meaning.
        List<QuestionAnalytics.OptionStat> forPullNote = q.getType() == QuestionType.MCQ ? options : List.of();

        return new QuestionAnalytics(
                q.getId(), q.getOrderIndex(), q.getType().name(), q.getText(), q.getMaxMarks(),
                onTime.size(), received.size() - onTime.size(), awaiting,
                percentCorrect, discrimination, options, notes(percentCorrect, discrimination, forPullNote));
    }

    /** Top group's average on this question minus bottom group's (each from 0 to 1), or null if too few students. */
    private static Double discrimination(Question q, List<String> ranked, Map<String, Response> answers) {
        if (ranked.size() < MIN_STUDENTS_FOR_DISCRIMINATION) {
            return null;
        }
        int groupSize = Math.max(1, (int) Math.round(ranked.size() * GROUP_SHARE));
        double top = averageScore(q, ranked.subList(0, groupSize), answers);
        double bottom = averageScore(q, ranked.subList(ranked.size() - groupSize, ranked.size()), answers);
        return Math.round((top - bottom) * 100) / 100.0;
    }

    /** Average score on this question, as a fraction of its marks; not answering counts as 0. */
    private static double averageScore(Question q, List<String> rollNos, Map<String, Response> answers) {
        return rollNos.stream()
                .map(roll -> ScoringService.marksFor(q, answers.get(roll + "#" + q.getId())))
                .mapToDouble(marks -> marks == null ? 0 : marks / q.getMaxMarks())
                .average()
                .orElse(0);
    }

    /** How many picked each option, in the question's real order. Empty for typed questions. */
    private static List<QuestionAnalytics.OptionStat> optionStats(Question q, List<Response> onTime) {
        if (!q.getType().isChoice()) {
            return List.of();
        }
        List<QuestionAnalytics.OptionStat> stats = new ArrayList<>();
        for (int i = 0; i < q.getOptions().size(); i++) {
            int option = i;
            long count = onTime.stream().filter(r -> r.getSelectedOptions().contains(option)).count();
            double percent = onTime.isEmpty() ? 0 : round(count * 100.0 / onTime.size());
            stats.add(new QuestionAnalytics.OptionStat(
                    i, q.getOptions().get(i), count, percent, q.getCorrectOptionIndexes().contains(i)));
        }
        return stats;
    }

    /** Plain-language warnings the instructor can act on. */
    private static List<String> notes(
            Double percentCorrect, Double discrimination, List<QuestionAnalytics.OptionStat> options) {
        List<String> notes = new ArrayList<>();
        if (discrimination != null && discrimination < 0) {
            notes.add("Weaker students did better than stronger ones: check the answer key.");
        } else if (discrimination != null && discrimination < WEAK_DISCRIMINATION) {
            notes.add("Doesn't separate strong and weak students well.");
        }
        if (percentCorrect != null && percentCorrect < HARD_BELOW_PERCENT) {
            notes.add("Hard: under " + (int) HARD_BELOW_PERCENT + "% correct. Worth going over in class.");
        }
        long correctCount = options.stream().filter(QuestionAnalytics.OptionStat::correct)
                .mapToLong(QuestionAnalytics.OptionStat::count).sum();
        options.stream()
                .filter(o -> !o.correct() && o.count() > correctCount)
                .max(Comparator.comparingLong(QuestionAnalytics.OptionStat::count))
                .ifPresent(o -> notes.add("More students chose \"" + o.text() + "\" than the correct option."));
        return notes;
    }

    /** One decimal place. */
    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
