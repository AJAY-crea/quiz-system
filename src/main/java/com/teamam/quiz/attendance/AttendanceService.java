package com.teamam.quiz.attendance;

import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attendance from participation: present iff the student answered at least ceil(threshold x
 * released questions). Late answers count (they prove the student was there) even though they
 * don't count toward the score. Rows are in roll-number order and include absentees.
 */
@Service
public class AttendanceService {

    private final QuizSessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final ResponseRepository responseRepository;
    private final double threshold;

    public AttendanceService(
            QuizSessionRepository sessionRepository,
            ParticipantRepository participantRepository,
            ResponseRepository responseRepository,
            @Value("${quiz.attendance.threshold:0.5}") double threshold) {
        if (threshold <= 0 || threshold > 1) {
            throw new IllegalArgumentException("quiz.attendance.threshold must be in (0, 1], was " + threshold);
        }
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.responseRepository = responseRepository;
        this.threshold = threshold;
    }

    /** Present/absent for every rostered student, in roll order, with flags (flags never change status). */
    @Transactional(readOnly = true)
    public AttendanceReport derive(Long sessionId) {
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));

        int released = session.getCurrentQuestionOrder();
        boolean joinedOnly = released == 0;
        long answersNeeded = (long) Math.ceil(threshold * released);

        Map<String, Participant> participants = participantRepository.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(Participant::getRollNo, Function.identity()));
        Map<String, Long> answers = responseRepository.countAnswersByRollNo(sessionId).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));

        List<AttendanceRow> rows = session.getRosterRollNumbers().stream()
                .sorted()
                .map(rollNo -> {
                    Participant participant = participants.get(rollNo);
                    boolean joined = participant != null;
                    long answered = answers.getOrDefault(rollNo, 0L);
                    boolean present = joinedOnly ? joined : answered >= answersNeeded;
                    return new AttendanceRow(
                            rollNo,
                            present ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT,
                            joined,
                            answered,
                            flagsOf(participant));
                })
                .toList();

        return new AttendanceReport(
                sessionId,
                released,
                threshold,
                joinedOnly ? AttendanceReport.BASIS_JOINED_ONLY : AttendanceReport.BASIS_PARTICIPATION,
                rows);
    }

    /** The proxy/device flags recorded for this student's join. */
    private static List<String> flagsOf(Participant participant) {
        if (participant == null || !participant.isFlagged() || participant.getFlagReason() == null) {
            return List.of();
        }
        return Arrays.asList(participant.getFlagReason().split(Participant.FLAG_SEPARATOR));
    }
}
