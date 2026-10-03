package com.teamam.quiz.repository;

import com.teamam.quiz.domain.IntegrityEvent;
import com.teamam.quiz.domain.IntegrityEventType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrityEventRepository extends JpaRepository<IntegrityEvent, Long> {

    long countBySessionIdAndQuestionIdAndRollNo(Long sessionId, Long questionId, String rollNo);

    long countBySessionIdAndQuestionIdAndRollNoAndEventType(
            Long sessionId, Long questionId, String rollNo, IntegrityEventType eventType);

    long countBySessionIdAndRollNo(Long sessionId, String rollNo);

    /** Rows of [rollNo, question orderIndex, eventType, count] for the instructor's review list. */
    @Query("select e.rollNo, e.question.orderIndex, e.eventType, count(e) from IntegrityEvent e"
            + " where e.session.id = :sessionId"
            + " group by e.rollNo, e.question.orderIndex, e.eventType"
            + " order by e.rollNo, e.question.orderIndex")
    List<Object[]> summarise(@Param("sessionId") Long sessionId);
}
