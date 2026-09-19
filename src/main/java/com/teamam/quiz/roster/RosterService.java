package com.teamam.quiz.roster;

import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Student;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.StudentRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RosterService {

    private final QuizSessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final ParticipantRepository participantRepository;

    public RosterService(
            QuizSessionRepository sessionRepository,
            StudentRepository studentRepository,
            ParticipantRepository participantRepository) {
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public void uploadRoster(Long sessionId, List<String> rollNumbers) {
        QuizSession session = requireSession(sessionId);
        for (String rollNo : rollNumbers) {
            if (studentRepository.findById(rollNo).isEmpty()) {
                studentRepository.save(new Student(rollNo));
            }
        }
        session.getRosterRollNumbers().addAll(rollNumbers);
        sessionRepository.save(session);
    }

    @Transactional
    public JoinResult join(Long sessionId, String rollNo, String deviceToken) {
        QuizSession session = requireSession(sessionId);

        if (!session.getRosterRollNumbers().contains(rollNo)) {
            return JoinResult.rejectedUnlisted(rollNo);
        }

        Optional<Participant> byRollNo = participantRepository.findBySessionIdAndRollNo(sessionId, rollNo);
        Optional<Participant> byDeviceToken =
                participantRepository.findBySessionIdAndDeviceToken(sessionId, deviceToken);

        boolean rollNoSeenOnDifferentDevice =
                byRollNo.isPresent() && !byRollNo.get().getDeviceToken().equals(deviceToken);
        boolean deviceSeenUnderDifferentRollNo =
                byDeviceToken.isPresent() && !byDeviceToken.get().getRollNo().equals(rollNo);

        Participant participant = byRollNo.orElseGet(() -> new Participant(session, rollNo, deviceToken));
        participant.setDeviceToken(deviceToken);

        if (rollNoSeenOnDifferentDevice || deviceSeenUnderDifferentRollNo) {
            String reason = flagReasonFor(rollNoSeenOnDifferentDevice, deviceSeenUnderDifferentRollNo);
            participant.flag(reason);
            participantRepository.save(participant);
            return JoinResult.acceptedFlagged(rollNo, deviceToken, reason);
        }

        participantRepository.save(participant);
        return JoinResult.accepted(rollNo, deviceToken);
    }

    private String flagReasonFor(boolean rollNoSeenOnDifferentDevice, boolean deviceSeenUnderDifferentRollNo) {
        if (rollNoSeenOnDifferentDevice && deviceSeenUnderDifferentRollNo) {
            return "roll number rejoined from a different device, and that device was previously used by a different roll number";
        }
        if (rollNoSeenOnDifferentDevice) {
            return "roll number rejoined from a different device";
        }
        return "device rejoined under a different roll number";
    }

    private QuizSession requireSession(Long sessionId) {
        return sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
    }
}
