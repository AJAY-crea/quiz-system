package com.teamam.quiz.doubts;

import com.teamam.quiz.domain.Doubt;
import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.DoubtRepository;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.session.NotAParticipantException;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module 9, basic form (mid-demo): anonymous doubts tagged to the question open when they were
 * asked. Only the instructor ever reads doubts; nothing here sends a doubt or its author to
 * another student. Upvoting and the after-lecture queue come later (design doc, Oct 10+).
 */
@Service
public class DoubtsService {

    public static final int MAX_LENGTH = 500;
    public static final int MAX_PER_STUDENT = 10;

    private final QuizSessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final QuestionRepository questionRepository;
    private final DoubtRepository doubtRepository;

    public DoubtsService(
            QuizSessionRepository sessionRepository,
            ParticipantRepository participantRepository,
            QuestionRepository questionRepository,
            DoubtRepository doubtRepository) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.questionRepository = questionRepository;
        this.doubtRepository = doubtRepository;
    }

    /** A student sends a doubt: checks the channel is on, the phone, the length and the per-student limit. */
    @Transactional
    public void submit(Long sessionId, String rollNo, String deviceToken, String text) {
        QuizSession session = requireSession(sessionId);
        if (!session.isDoubtsEnabled()) {
            throw new DoubtRefusedException("The doubts channel is turned off for this lecture.");
        }
        Participant participant = participantRepository
                .findBySessionIdAndRollNo(sessionId, rollNo)
                .orElseThrow(() -> new NotAParticipantException("roll number has not joined this session"));
        if (!participant.getDeviceToken().equals(deviceToken)) {
            throw new NotAParticipantException("this device is not the one the roll number last joined from");
        }
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Write your doubt first.");
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Doubts can be at most " + MAX_LENGTH + " characters.");
        }
        if (doubtRepository.countBySessionIdAndAuthorRollNo(sessionId, rollNo) >= MAX_PER_STUDENT) {
            throw new DoubtRefusedException(
                    "You've sent " + MAX_PER_STUDENT + " doubts in this lecture; please ask the rest after class.");
        }
        Question openQuestion = session.getCurrentQuestionOrder() == 0
                ? null
                : questionRepository.findBySessionIdAndOrderIndex(sessionId, session.getCurrentQuestionOrder())
                        .orElse(null);
        doubtRepository.save(new Doubt(session, openQuestion, rollNo, trimmed));
    }

    /** Open doubts first (oldest first, like a queue), then dismissed ones. */
    @Transactional(readOnly = true)
    public List<DoubtView> forInstructor(Long sessionId) {
        boolean showAuthors = requireSession(sessionId).isDoubtAuthorsVisible();
        return doubtRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId).stream()
                .sorted(Comparator.comparing(Doubt::isDismissed))
                .map(d -> new DoubtView(
                        d.getId(),
                        d.getQuestion() == null ? null : d.getQuestion().getId(),
                        d.getQuestion() == null ? null : d.getQuestion().getOrderIndex(),
                        d.getText(),
                        showAuthors ? d.getAuthorRollNo() : null,
                        d.isDismissed(),
                        d.getCreatedAt()))
                .toList();
    }

    /** Instructor marks a doubt as dealt with. */
    @Transactional
    public void dismiss(Long sessionId, Long doubtId) {
        Doubt doubt = doubtRepository
                .findById(doubtId)
                .filter(d -> d.getSession().getId().equals(sessionId))
                .orElseThrow(() -> new NoSuchElementException("doubt not found: " + doubtId));
        doubt.setDismissed(true);
        doubtRepository.save(doubt);
    }

    /** Instructor turns the doubts channel on or off. */
    @Transactional
    public void setEnabled(Long sessionId, boolean enabled) {
        QuizSession session = requireSession(sessionId);
        session.setDoubtsEnabled(enabled);
        sessionRepository.save(session);
    }

    /** Instructor chooses whether to see who asked each doubt. */
    @Transactional
    public void setAuthorsVisible(Long sessionId, boolean visible) {
        QuizSession session = requireSession(sessionId);
        session.setDoubtAuthorsVisible(visible);
        sessionRepository.save(session);
    }

    /** Is the doubts channel on? */
    @Transactional(readOnly = true)
    public boolean isEnabled(Long sessionId) {
        return requireSession(sessionId).isDoubtsEnabled();
    }

    /** Does the instructor currently see authors? */
    @Transactional(readOnly = true)
    public boolean areAuthorsVisible(Long sessionId) {
        return requireSession(sessionId).isDoubtAuthorsVisible();
    }

    /** Loads the session or fails with "not found". */
    private QuizSession requireSession(Long sessionId) {
        return sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
    }
}
