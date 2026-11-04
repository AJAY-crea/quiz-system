package com.teamam.quiz.doubts;

import com.teamam.quiz.domain.Doubt;
import com.teamam.quiz.domain.DoubtUpvote;
import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.DoubtRepository;
import com.teamam.quiz.repository.DoubtUpvoteRepository;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.session.NotAParticipantException;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Module 9: anonymous doubts tagged to the question open when they were asked. Students see open
 * doubts and upvote them, so the instructor's queue puts what most of the class is stuck on first.
 * An author's roll number reaches only the instructor, and only if she turns that on.
 */
@Service
public class DoubtsService {

    public static final int MAX_LENGTH = 500;
    public static final int MAX_PER_STUDENT = 10;

    private final QuizSessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final QuestionRepository questionRepository;
    private final DoubtRepository doubtRepository;
    private final DoubtUpvoteRepository upvoteRepository;
    private final TransactionTemplate writeTransaction;

    public DoubtsService(
            QuizSessionRepository sessionRepository,
            ParticipantRepository participantRepository,
            QuestionRepository questionRepository,
            DoubtRepository doubtRepository,
            DoubtUpvoteRepository upvoteRepository,
            PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.questionRepository = questionRepository;
        this.doubtRepository = doubtRepository;
        this.upvoteRepository = upvoteRepository;
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    /** A student sends a doubt: checks the channel is on, the phone, the length and the per-student limit. */
    @Transactional
    public void submit(Long sessionId, String rollNo, String deviceToken, String text) {
        QuizSession session = requireSession(sessionId);
        if (!session.isDoubtsEnabled()) {
            throw new DoubtRefusedException("The doubts channel is turned off for this lecture.");
        }
        requireThisPhone(sessionId, rollNo, deviceToken);
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

    /** A student upvotes a doubt. Pressing again changes nothing; own and dismissed doubts can't be upvoted. */
    public void upvote(Long sessionId, Long doubtId, String rollNo, String deviceToken) {
        QuizSession session = requireSession(sessionId);
        requireThisPhone(sessionId, rollNo, deviceToken);
        if (!session.isDoubtsEnabled() || !session.isDoubtsShared()) {
            throw new DoubtRefusedException("Upvoting is turned off for this lecture.");
        }
        Doubt doubt = doubtRepository
                .findByIdAndSessionId(doubtId, sessionId)
                .orElseThrow(() -> new NoSuchElementException("doubt not found: " + doubtId));
        if (doubt.isDismissed()) {
            throw new DoubtRefusedException("The instructor has already dealt with this doubt.");
        }
        if (rollNo.equals(doubt.getAuthorRollNo())) {
            throw new DoubtRefusedException("You can't upvote your own doubt.");
        }
        try {
            // Both writes or neither. The exists check is the common path; the unique constraint is the real guard.
            writeTransaction.executeWithoutResult(status -> {
                if (!upvoteRepository.existsByDoubtIdAndRollNo(doubtId, rollNo)) {
                    upvoteRepository.saveAndFlush(new DoubtUpvote(doubt, rollNo));
                    doubtRepository.addUpvote(doubtId);
                }
            });
        } catch (DataIntegrityViolationException doubleTap) {
            // The same student's other request got there first; it has been counted once, as it should.
        }
    }

    /** A student takes back their upvote. Pressing again (or with no upvote) changes nothing. */
    public void removeUpvote(Long sessionId, Long doubtId, String rollNo, String deviceToken) {
        requireSession(sessionId);
        requireThisPhone(sessionId, rollNo, deviceToken);
        doubtRepository
                .findByIdAndSessionId(doubtId, sessionId)
                .orElseThrow(() -> new NoSuchElementException("doubt not found: " + doubtId));
        // Delete and decrement together: the count only drops if this student's upvote really existed.
        writeTransaction.executeWithoutResult(status -> {
            if (upvoteRepository.removeVote(doubtId, rollNo) == 1) {
                doubtRepository.removeUpvote(doubtId);
            }
        });
    }

    /** The open doubts a student can see: most upvoted first, then oldest. Never says who asked. */
    @Transactional(readOnly = true)
    public DoubtQueue queueFor(Long sessionId, String rollNo, String deviceToken) {
        QuizSession session = requireSession(sessionId);
        requireThisPhone(sessionId, rollNo, deviceToken);
        if (!session.isDoubtsEnabled() || !session.isDoubtsShared()) {
            return new DoubtQueue(false, List.of());
        }
        Set<Long> upvoted = upvoteRepository.doubtIdsUpvotedBy(sessionId, rollNo);
        return new DoubtQueue(true, doubtRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId).stream()
                .filter(d -> !d.isDismissed())
                .sorted(Comparator.comparingInt(Doubt::getUpvotes).reversed())
                .map(d -> new DoubtQueue.Item(
                        d.getId(),
                        d.getQuestion() == null ? null : d.getQuestion().getOrderIndex(),
                        d.getText(),
                        d.getUpvotes(),
                        upvoted.contains(d.getId()),
                        rollNo.equals(d.getAuthorRollNo())))
                .toList());
    }

    /** The instructor's queue: open doubts, most upvoted first (ties: oldest first), then dismissed ones. */
    @Transactional(readOnly = true)
    public List<DoubtView> forInstructor(Long sessionId) {
        boolean showAuthors = requireSession(sessionId).isDoubtAuthorsVisible();
        return doubtRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId).stream()
                .sorted(Comparator.comparing(Doubt::isDismissed)
                        .thenComparing(Comparator.comparingInt(Doubt::getUpvotes).reversed()))
                .map(d -> new DoubtView(
                        d.getId(),
                        d.getQuestion() == null ? null : d.getQuestion().getId(),
                        d.getQuestion() == null ? null : d.getQuestion().getOrderIndex(),
                        d.getText(),
                        showAuthors ? d.getAuthorRollNo() : null,
                        d.isDismissed(),
                        d.getCreatedAt(),
                        d.getUpvotes()))
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

    /** Instructor chooses whether students see and upvote each other's doubts. */
    @Transactional
    public void setShared(Long sessionId, boolean shared) {
        QuizSession session = requireSession(sessionId);
        session.setDoubtsShared(shared);
        sessionRepository.save(session);
    }

    /** Do students see each other's doubts? */
    @Transactional(readOnly = true)
    public boolean isShared(Long sessionId) {
        return requireSession(sessionId).isDoubtsShared();
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

    /** Fails unless this roll number joined the session from this phone. */
    private void requireThisPhone(Long sessionId, String rollNo, String deviceToken) {
        Participant participant = participantRepository
                .findBySessionIdAndRollNo(sessionId, rollNo)
                .orElseThrow(() -> new NotAParticipantException("roll number has not joined this session"));
        if (!participant.getDeviceToken().equals(deviceToken)) {
            throw new NotAParticipantException("this device is not the one the roll number last joined from");
        }
    }

    /** Loads the session or fails with "not found". */
    private QuizSession requireSession(Long sessionId) {
        return sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
    }
}
