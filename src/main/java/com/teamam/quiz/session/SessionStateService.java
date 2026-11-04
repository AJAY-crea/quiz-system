package com.teamam.quiz.session;

import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.questionbank.RandomisationService;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.timing.TimingService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restart recovery for the browser side: everything here is rebuilt from the database, so it
 * returns the same answer before and after the instructor's app restarts.
 */
@Service
public class SessionStateService {

    private final QuizSessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final QuestionRepository questionRepository;
    private final ResponseRepository responseRepository;
    private final TimingService timingService;
    private final RandomisationService randomisationService;

    public SessionStateService(
            QuizSessionRepository sessionRepository,
            ParticipantRepository participantRepository,
            QuestionRepository questionRepository,
            ResponseRepository responseRepository,
            TimingService timingService,
            RandomisationService randomisationService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
        this.timingService = timingService;
        this.randomisationService = randomisationService;
    }

    /** This phone's view: open question with ITS option order, server-computed time left, already answered? */
    @Transactional(readOnly = true)
    public SessionStateView currentState(Long sessionId, String rollNo, String deviceToken) {
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));

        Participant participant = participantRepository
                .findBySessionIdAndRollNo(sessionId, rollNo)
                .orElseThrow(() -> new NotAParticipantException("roll number has not joined this session"));
        if (!participant.getDeviceToken().equals(deviceToken)) {
            throw new NotAParticipantException("this device is not the one the roll number last joined from");
        }

        if (session.getCurrentQuestionOrder() == 0) {
            return new SessionStateView(sessionId, session.getStatus().name(), null, null, false, List.of(), null);
        }

        Question question = questionRepository
                .findBySessionIdAndOrderIndex(sessionId, session.getCurrentQuestionOrder())
                .orElseThrow(() -> new IllegalStateException("current question missing for session " + sessionId));

        long remainingMs = Math.max(
                0, Duration.between(Instant.now(), timingService.deadlineFor(question.getId())).toMillis());
        Response mine = responseRepository
                .findBySessionIdAndQuestionIdAndRollNo(sessionId, question.getId(), rollNo)
                .orElse(null);
        boolean alreadyAnswered = mine != null;

        StudentQuestionView view = new StudentQuestionView(
                question.getId(),
                question.getOrderIndex(),
                question.getType().name(),
                question.getText(),
                randomisationService.shuffleFor(rollNo, question),
                question.getDurationSeconds() + question.getExtensionSeconds());

        return new SessionStateView(
                sessionId, session.getStatus().name(), view, remainingMs, alreadyAnswered,
                myChoice(rollNo, question, mine == null ? null : mine.getSelectedOptions()),
                mine == null ? null : mine.getTypedAnswer());
    }

    /** Where the stored options sit in this student's shuffled view (empty if none). */
    private List<Integer> myChoice(String rollNo, Question question, List<Integer> stored) {
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        return IntStream.range(0, question.getOptions().size())
                .filter(position -> stored.contains(randomisationService.remap(rollNo, question, position)))
                .boxed()
                .toList();
    }
}
