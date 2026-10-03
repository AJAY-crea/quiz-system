package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Doubt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoubtRepository extends JpaRepository<Doubt, Long> {

    List<Doubt> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId);

    long countBySessionIdAndAuthorRollNo(Long sessionId, String authorRollNo);
}
