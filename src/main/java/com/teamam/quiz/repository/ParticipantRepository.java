package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Participant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findBySessionIdAndRollNo(Long sessionId, String rollNo);

    Optional<Participant> findBySessionIdAndDeviceToken(Long sessionId, String deviceToken);
}
