package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Response;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Database access for answers: find one, count them, or count per student for attendance. */
public interface ResponseRepository extends JpaRepository<Response, Long> {

    /** Rows of [rollNo, number of answers stored] for one session; late answers included. */
    @Query("select r.rollNo, count(r) from Response r where r.session.id = :sessionId group by r.rollNo")
    List<Object[]> countAnswersByRollNo(@Param("sessionId") Long sessionId);

    long countBySessionIdAndQuestionId(Long sessionId, Long questionId);

    List<Response> findBySessionId(Long sessionId);

    List<Response> findBySessionIdAndQuestionId(Long sessionId, Long questionId);

    /** On-time typed answers on this question that the instructor hasn't marked yet. */
    long countBySessionIdAndQuestionIdAndOnTimeTrueAndAwardedMarksIsNull(Long sessionId, Long questionId);

    Optional<Response> findBySessionIdAndQuestionIdAndRollNo(Long sessionId, Long questionId, String rollNo);

    long countBySessionIdAndQuestionIdAndRollNo(Long sessionId, Long questionId, String rollNo);

    /**
     * Replaces a stored on-time choice with a later one, in one database statement: it only happens if
     * the new tap is newer (bigger seq), so a slow resend of an older tap can never undo a newer one.
     * Returns 1 if replaced, 0 if not.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Response r set r.selectedOptionIndex = :single, r.selectedOptionSet = :set, r.correct = :correct,"
            + " r.submissionId = :submissionId, r.submittedAt = :arrivedAt, r.answerSeq = :seq"
            + " where r.id = :id and r.onTime = true and (r.answerSeq is null or r.answerSeq < :seq)")
    int changeChoice(
            @Param("id") Long id,
            @Param("single") Integer single,
            @Param("set") String set,
            @Param("correct") Boolean correct,
            @Param("submissionId") String submissionId,
            @Param("arrivedAt") java.time.Instant arrivedAt,
            @Param("seq") Long seq);

    boolean existsBySessionIdAndQuestionIdAndRollNo(Long sessionId, Long questionId, String rollNo);
}
