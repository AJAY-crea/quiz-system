package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Doubt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Database access for doubts. */
public interface DoubtRepository extends JpaRepository<Doubt, Long> {

    List<Doubt> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId);

    long countBySessionIdAndAuthorRollNo(Long sessionId, String authorRollNo);

    Optional<Doubt> findByIdAndSessionId(Long id, Long sessionId);

    /** Adds one in the database itself, so two students upvoting at once can't overwrite each other. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Doubt d set d.upvotes = d.upvotes + 1 where d.id = :doubtId")
    void addUpvote(@Param("doubtId") Long doubtId);
}
