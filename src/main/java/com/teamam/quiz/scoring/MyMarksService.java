package com.teamam.quiz.scoring;

import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.session.NotAParticipantException;
import com.teamam.quiz.timing.TimingService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's own marks, off until the instructor publishes them. A question still open is left out:
 * otherwise anyone who answered could see whether they were right and tell friends who haven't.
 */
@Service
public class MyMarksService {

    private final QuizSessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final QuestionRepository questionRepository;
    private final ResponseRepository responseRepository;
    private final TimingService timingService;

    public MyMarksService(
            QuizSessionRepository sessionRepository,
            ParticipantRepository participantRepository,
            QuestionRepository questionRepository,
            ResponseRepository responseRepository,
            TimingService timingService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
        this.timingService = timingService;
    }

    /** This student's marks on every closed question, if published; only from the phone they joined on. */
    @Transactional(readOnly = true)
    public MyMarks forStudent(Long sessionId, String rollNo, String deviceToken) {
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
        Participant participant = participantRepository
                .findBySessionIdAndRollNo(sessionId, rollNo)
                .orElseThrow(() -> new NotAParticipantException("roll number has not joined this session"));
        if (!participant.getDeviceToken().equals(deviceToken)) {
            throw new NotAParticipantException("this device is not the one the roll number last joined from");
        }
        if (!session.isMarksVisibleToStudents()) {
            return MyMarks.notPublished();
        }

        Instant now = Instant.now();
        List<MyMarks.Row> rows = new ArrayList<>();
        double total = 0;
        int outOf = 0;
        for (Question q : questionRepository.findBySessionIdOrderByOrderIndex(sessionId)) {
            if (q.getReleasedAt() == null || now.isBefore(timingService.deadlineFor(q.getId()))) {
                continue;
            }
            Response r = responseRepository.findBySessionIdAndQuestionIdAndRollNo(sessionId, q.getId(), rollNo).orElse(null);
            Double marks = ScoringService.marksFor(q, r);
            total += marks == null ? 0 : marks;
            outOf += q.getMaxMarks();
            rows.add(new MyMarks.Row(q.getOrderIndex(), marks, q.getMaxMarks(), status(q, r)));
        }
        return new MyMarks(true, rows, total, outOf);
    }

    /** Publishes or hides marks. */
    @Transactional
    public void setPublished(Long sessionId, boolean published) {
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
        session.setMarksVisibleToStudents(published);
        sessionRepository.save(session);
    }

    /** The word shown next to a mark. */
    private static String status(Question q, Response r) {
        if (r == null) {
            return "NOT_ANSWERED";
        }
        if (!r.isOnTime()) {
            return "LATE";
        }
        return ScoringService.awaitsMarking(q, r) ? "AWAITING_MARKS" : "MARKED";
    }
}
