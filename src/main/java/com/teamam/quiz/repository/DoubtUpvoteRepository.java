package com.teamam.quiz.repository;

import com.teamam.quiz.domain.DoubtUpvote;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Database access for doubt upvotes. */
public interface DoubtUpvoteRepository extends JpaRepository<DoubtUpvote, Long> {

    boolean existsByDoubtIdAndRollNo(Long doubtId, String rollNo);

    /** Ids of the doubts in this session that this student has upvoted. */
    @Query("select u.doubt.id from DoubtUpvote u where u.doubt.session.id = :sessionId and u.rollNo = :rollNo")
    Set<Long> doubtIdsUpvotedBy(@Param("sessionId") Long sessionId, @Param("rollNo") String rollNo);
}
