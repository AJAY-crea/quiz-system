package com.teamam.quiz.resilience;

import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.questionbank.RandomisationService;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.scoring.ScoringService;
import com.teamam.quiz.timing.TimingService;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Single entry point for every incoming answer. Idempotent: a resend of an answer that was
 * already stored returns DUPLICATE instead of storing it twice.
 *
 * Must not be called inside an outer transaction: the insert commits in its own transaction so
 * a unique-constraint violation can be caught here and turned into DUPLICATE.
 */
@Service
public class ResilienceService {

    private static final int MAX_SUBMISSION_ID_LENGTH = 64;
    private static final int MAX_TYPED_ANSWER_LENGTH = 4000;

    private final QuizSessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final QuestionRepository questionRepository;
    private final ResponseRepository responseRepository;
    private final TimingService timingService;
    private final RandomisationService randomisationService;
    private final ScoringService scoringService;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate insertTransaction;

    public ResilienceService(
            QuizSessionRepository sessionRepository,
            ParticipantRepository participantRepository,
            QuestionRepository questionRepository,
            ResponseRepository responseRepository,
            TimingService timingService,
            RandomisationService randomisationService,
            ScoringService scoringService,
            PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
        this.timingService = timingService;
        this.randomisationService = randomisationService;
        this.scoringService = scoringService;

        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);

        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Takes one answer: checks it, judges timing, un-shuffles + scores it, saves it ONCE, says ACCEPTED/DUPLICATE/REJECTED. */
    public AcceptResult accept(Long sessionId, AnswerSubmission submission) {
        // Step 0: stamp arrival before any work, so server processing time never counts against the student.
        Instant arrivedAt = Instant.now();

        String shapeProblem = shapeProblem(submission);
        if (shapeProblem != null) {
            return AcceptResult.rejected(submission == null ? null : submission.submissionId(), shapeProblem);
        }

        Response response;
        try {
            response = readTransaction.execute(status -> buildResponse(sessionId, submission, arrivedAt));
        } catch (Rejection rejection) {
            return AcceptResult.rejected(submission.submissionId(), rejection.getMessage());
        }

        // Fast path for the common resend case. Not the real guard: two copies can both pass this check.
        Optional<Response> alreadyStored = findStored(sessionId, submission);
        if (alreadyStored.isPresent()) {
            return duplicateOf(alreadyStored.get(), submission);
        }

        try {
            insertTransaction.executeWithoutResult(status -> responseRepository.saveAndFlush(response));
        } catch (DataIntegrityViolationException raceLost) {
            // The real guard: the (session, question, roll_no) unique constraint rejected a concurrent copy.
            return findStored(sessionId, submission)
                    .map(stored -> duplicateOf(stored, submission))
                    .orElseThrow(() -> raceLost);
        }
        return AcceptResult.accepted(submission.submissionId(), response.isOnTime());
    }

    /** All the checks (right phone, released question, valid option) and builds the answer row to save. */
    private Response buildResponse(Long sessionId, AnswerSubmission s, Instant arrivedAt) {
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new Rejection("unknown session"));

        Participant participant = participantRepository
                .findBySessionIdAndRollNo(sessionId, s.rollNo())
                .orElseThrow(() -> new Rejection("this roll number has not joined the session"));
        if (!participant.getDeviceToken().equals(s.deviceToken())) {
            throw new Rejection("this device is not the one the roll number last joined from");
        }

        Question question = questionRepository
                .findById(s.questionId())
                .orElseThrow(() -> new Rejection("unknown question"));
        if (!question.getSession().getId().equals(sessionId)) {
            throw new Rejection("question does not belong to this session");
        }
        if (question.getReleasedAt() == null) {
            throw new Rejection("question has not been released yet");
        }

        Response response = new Response(session, question, s.rollNo());
        response.setSubmissionId(s.submissionId());
        response.setSubmittedAt(arrivedAt);
        response.setOnTime(timingService.isOnTime(question.getId(), arrivedAt));

        if (question.getType() == QuestionType.MCQ) {
            Integer chosen = s.chosenIndex();
            if (chosen == null || chosen < 0 || chosen >= question.getOptions().size()) {
                throw new Rejection("chosen option is out of range");
            }
            response.setSelectedOptionIndex(randomisationService.remap(s.rollNo(), question, chosen));
            response.setCorrect(scoringService.scoreMcq(s.rollNo(), question, chosen));
        } else {
            String typed = s.typedAnswer();
            if (typed == null || typed.isBlank()) {
                throw new Rejection("typed answer is empty");
            }
            if (typed.length() > MAX_TYPED_ANSWER_LENGTH) {
                throw new Rejection("typed answer is longer than " + MAX_TYPED_ANSWER_LENGTH + " characters");
            }
            response.setTypedAnswer(typed);
        }
        return response;
    }

    /** Quick check that required fields are present; returns what's wrong, or null if fine. */
    private static String shapeProblem(AnswerSubmission s) {
        if (s == null) {
            return "empty submission";
        }
        if (s.submissionId() == null || s.submissionId().isBlank()
                || s.submissionId().length() > MAX_SUBMISSION_ID_LENGTH) {
            return "missing or invalid submissionId";
        }
        if (s.rollNo() == null || s.rollNo().isBlank()) {
            return "missing rollNo";
        }
        if (s.deviceToken() == null || s.deviceToken().isBlank()) {
            return "missing deviceToken";
        }
        if (s.questionId() == null) {
            return "missing questionId";
        }
        return null;
    }

    /** The answer already stored for this student and question, if any. */
    private Optional<Response> findStored(Long sessionId, AnswerSubmission s) {
        return responseRepository.findBySessionIdAndQuestionIdAndRollNo(sessionId, s.questionId(), s.rollNo());
    }

    /** Builds the DUPLICATE reply, saying whether it was the same answer resent. */
    private static AcceptResult duplicateOf(Response stored, AnswerSubmission s) {
        boolean sameSubmission = Objects.equals(stored.getSubmissionId(), s.submissionId());
        return AcceptResult.duplicate(s.submissionId(), stored.isOnTime(), sameSubmission);
    }

    /** A submission the server refuses to store; its message is sent back to the student. */
    private static final class Rejection extends RuntimeException {
        Rejection(String reason) {
            super(reason, null, false, false);
        }
    }
}
