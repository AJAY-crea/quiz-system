package com.teamam.quiz.integrity;

import com.teamam.quiz.domain.IntegrityEvent;
import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.IntegrityEventRepository;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.timing.TimingService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module 7: records focus-loss, fullscreen-exit and paste signals against (student, question).
 * Advisory only: nothing here reads or writes a Response, so a signal can never change a mark.
 * Phones fire these for innocent reasons too (screen lock, an incoming call), which is why they
 * are shown to the instructor as counts to judge, not as accusations.
 */
@Service
public class IntegrityService {

    public static final int MAX_EVENTS_PER_STUDENT_QUESTION = 50;

    private final QuizSessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final QuestionRepository questionRepository;
    private final IntegrityEventRepository eventRepository;
    private final TimingService timingService;

    public IntegrityService(
            QuizSessionRepository sessionRepository,
            ParticipantRepository participantRepository,
            QuestionRepository questionRepository,
            IntegrityEventRepository eventRepository,
            TimingService timingService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.questionRepository = questionRepository;
        this.eventRepository = eventRepository;
        this.timingService = timingService;
    }

    @Transactional
    public IntegrityOutcome record(Long sessionId, IntegrityReport report) {
        Instant arrivedAt = Instant.now();
        if (report == null || report.rollNo() == null || report.deviceToken() == null
                || report.questionId() == null || report.eventType() == null) {
            return IntegrityOutcome.REJECTED;
        }
        Optional<QuizSession> session = sessionRepository.findById(sessionId);
        Optional<Participant> participant = participantRepository.findBySessionIdAndRollNo(sessionId, report.rollNo());
        if (session.isEmpty() || participant.isEmpty()
                || !participant.get().getDeviceToken().equals(report.deviceToken())) {
            return IntegrityOutcome.REJECTED;
        }
        Optional<Question> question = questionRepository.findById(report.questionId());
        if (question.isEmpty()
                || !question.get().getSession().getId().equals(sessionId)
                || question.get().getReleasedAt() == null
                || !timingService.isOnTime(report.questionId(), arrivedAt)) {
            return IntegrityOutcome.REJECTED;
        }
        long alreadyStored =
                eventRepository.countBySessionIdAndQuestionIdAndRollNo(sessionId, report.questionId(), report.rollNo());
        if (alreadyStored >= MAX_EVENTS_PER_STUDENT_QUESTION) {
            return IntegrityOutcome.CAPPED;
        }
        eventRepository.save(new IntegrityEvent(session.get(), question.get(), report.rollNo(), report.eventType()));
        return IntegrityOutcome.RECORDED;
    }

    @Transactional(readOnly = true)
    public List<IntegritySummaryRow> summary(Long sessionId) {
        return eventRepository.summarise(sessionId).stream()
                .map(row -> new IntegritySummaryRow(
                        (String) row[0], (Integer) row[1], row[2].toString(), (Long) row[3]))
                .toList();
    }
}
