package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Response;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Database access for answers: find one, count them, or count per student for attendance. */
public interface ResponseRepository extends JpaRepository<Response, Long> {

    /** Rows of [rollNo, number of answers stored] for one session; late answers included. */
    @Query("select r.rollNo, count(r) from Response r where r.session.id = :sessionId group by r.rollNo")
    List<Object[]> countAnswersByRollNo(@Param("sessionId") Long sessionId);

    long countBySessionIdAndQuestionId(Long sessionId, Long questionId);

    List<Response> findBySessionId(Long sessionId);

    Optional<Response> findBySessionIdAndQuestionIdAndRollNo(Long sessionId, Long questionId, String rollNo);

    long countBySessionIdAndQuestionIdAndRollNo(Long sessionId, Long questionId, String rollNo);

    boolean existsBySessionIdAndQuestionIdAndRollNo(Long sessionId, Long questionId, String rollNo);
}
