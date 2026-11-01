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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
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

    /**
     * A "left the page" report is sent when the student comes BACK (that's when the time away is
     * known), or later if the phone was offline, so reports may arrive a little after the deadline.
     */
    public static final Duration LATE_REPORT_WINDOW = Duration.ofMinutes(2);

    /** Longer than any sensible lecture question; caps a phone-reported away time. */
    private static final long MAX_AWAY_MS = Duration.ofHours(1).toMillis();

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

    /** Stores one signal if it's from the right phone, for a released question (up to 2 min after its deadline), under the cap. */
    @Transactional
    public IntegrityOutcome record(Long sessionId, IntegrityReport report) {
        Instant arrivedAt = Instant.now();
        if (report == null || report.rollNo() == null || report.deviceToken() == null
                || report.questionId() == null || report.eventType() == null) {
            return IntegrityOutcome.REJECTED;
        }
        Optional<QuizSession> session = sessionRepository.findById(sessionId);
        Optional<Participant> participant = participantRepository.findBySessionIdAndRollNo(sessionId, report.rollNo());
        if (session.isEmpty() || !session.get().isIntegrityEnabled() || participant.isEmpty()
                || !participant.get().getDeviceToken().equals(report.deviceToken())) {
            return IntegrityOutcome.REJECTED;
        }
        Optional<Question> question = questionRepository.findById(report.questionId());
        if (question.isEmpty()
                || !question.get().getSession().getId().equals(sessionId)
                || question.get().getReleasedAt() == null
                || arrivedAt.isAfter(timingService.deadlineFor(report.questionId()).plus(LATE_REPORT_WINDOW))) {
            return IntegrityOutcome.REJECTED;
        }
        long alreadyStored =
                eventRepository.countBySessionIdAndQuestionIdAndRollNo(sessionId, report.questionId(), report.rollNo());
        if (alreadyStored >= MAX_EVENTS_PER_STUDENT_QUESTION) {
            return IntegrityOutcome.CAPPED;
        }
        Long awayMs = report.awayMs() == null || report.awayMs() < 0 ? null : Math.min(report.awayMs(), MAX_AWAY_MS);
        eventRepository.save(
                new IntegrityEvent(session.get(), question.get(), report.rollNo(), report.eventType(), awayMs));
        return IntegrityOutcome.RECORDED;
    }

    /** Instructor turns recording on or off (design doc risk #3: if signals prove too noisy). */
    @Transactional
    public void setEnabled(Long sessionId, boolean enabled) {
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
        session.setIntegrityEnabled(enabled);
        sessionRepository.save(session);
    }

    /** Instructor wipes this session's signals (design doc risk #3). */
    @Transactional
    public void clear(Long sessionId) {
        eventRepository.deleteBySessionId(sessionId);
    }

    /** Counts per student, question and signal type, for the dashboard. */
    @Transactional(readOnly = true)
    public List<IntegritySummaryRow> summary(Long sessionId) {
        return eventRepository.summarise(sessionId).stream()
                .map(row -> new IntegritySummaryRow(
                        (String) row[0], (Integer) row[1], row[2].toString(), (Long) row[3],
                        Math.round(((Number) row[4]).doubleValue() / 1000)))
                .toList();
    }
}
