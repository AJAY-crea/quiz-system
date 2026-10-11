package com.teamam.quiz.roster;

import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Student;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.StudentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Module 1 (Ajay): the class list and the rules for who may join, from which phone. */
@Service
public class RosterService {

    private final QuizSessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final ParticipantRepository participantRepository;
    private final JoinWindowService joinWindowService;

    public RosterService(
            QuizSessionRepository sessionRepository,
            StudentRepository studentRepository,
            ParticipantRepository participantRepository,
            JoinWindowService joinWindowService) {
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.participantRepository = participantRepository;
        this.joinWindowService = joinWindowService;
    }

    /** Adds roll numbers (without names) to a session's roster. */
    @Transactional
    public void uploadRoster(Long sessionId, List<String> rollNumbers) {
        uploadRosterEntries(sessionId, rollNumbers.stream().map(r -> new RosterEntry(r, null)).toList());
    }

    /** Adds students to the session's roster; a name in the file updates the stored name. */
    @Transactional
    public void uploadRosterEntries(Long sessionId, List<RosterEntry> entries) {
        QuizSession session = requireSession(sessionId);
        for (RosterEntry entry : entries) {
            Student student = studentRepository.findById(entry.rollNo()).orElseGet(() -> new Student(entry.rollNo()));
            if (entry.name() != null) {
                student.setName(entry.name());
            }
            studentRepository.save(student);
            session.getRosterRollNumbers().add(entry.rollNo());
        }
        sessionRepository.save(session);
    }

    /**
     * Join rules, in order:
     * 1. the same roll number rejoining from the same device is always let back in (reload, reconnect);
     * 2. anything else needs a valid ticket from scanning the live QR;
     * 3. the roll number must be on the roster;
     * 4. device mismatches are flagged for the instructor, never blocked.
     */
    @Transactional
    public JoinResult join(Long sessionId, String rollNo, String deviceToken, String ticket) {
        QuizSession session = requireSession(sessionId);
        Optional<Participant> byRollNo = participantRepository.findBySessionIdAndRollNo(sessionId, rollNo);

        boolean sameDeviceRejoin = byRollNo.isPresent() && byRollNo.get().getDeviceToken().equals(deviceToken);
        if (sameDeviceRejoin) {
            return JoinResult.accepted(rollNo, deviceToken);
        }

        if (!joinWindowService.isValidTicket(sessionId, ticket)) {
            return JoinResult.rejectedInvalidTicket(rollNo);
        }

        if (!session.getRosterRollNumbers().contains(rollNo)) {
            return JoinResult.rejectedUnlisted(rollNo);
        }

        List<String> reasons = new ArrayList<>();
        if (byRollNo.isPresent()) {
            reasons.add("roll number rejoined from a different device");
        }
        participantRepository.findAllBySessionIdAndDeviceToken(sessionId, deviceToken).stream()
                .filter(other -> !other.getRollNo().equals(rollNo))
                .map(other -> "device was already used by " + other.getRollNo() + " in this session")
                .forEach(reasons::add);

        List<Participant> earlierSessions =
                participantRepository.findByDeviceTokenAndRollNoNotAndSessionIdNot(deviceToken, rollNo, sessionId);
        if (!earlierSessions.isEmpty()) {
            String otherRolls = earlierSessions.stream()
                    .map(Participant::getRollNo)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));
            reasons.add("device was used by " + otherRolls + " in an earlier session");
        }

        Participant participant = byRollNo.orElseGet(() -> new Participant(session, rollNo, deviceToken));
        participant.setDeviceToken(deviceToken);
        reasons.forEach(participant::flag);
        participantRepository.save(participant);

        return reasons.isEmpty()
                ? JoinResult.accepted(rollNo, deviceToken)
                : JoinResult.acceptedFlagged(rollNo, deviceToken, String.join(Participant.FLAG_SEPARATOR, reasons));
    }

    /** Loads the session or fails with "not found". */
    private QuizSession requireSession(Long sessionId) {
        return sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
    }
}
