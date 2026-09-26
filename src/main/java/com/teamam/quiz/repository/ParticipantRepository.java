package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Participant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findBySessionIdAndRollNo(Long sessionId, String rollNo);

    /** A list, not Optional: after a flagged shared-device join, several participants hold the same token. */
    List<Participant> findAllBySessionIdAndDeviceToken(Long sessionId, String deviceToken);

    /** Earlier joins from this device under a different roll number, in any session. */
    List<Participant> findByDeviceTokenAndRollNoNotAndSessionIdNot(String deviceToken, String rollNo, Long sessionId);

    List<Participant> findBySessionId(Long sessionId);

    long countBySessionId(Long sessionId);
}
