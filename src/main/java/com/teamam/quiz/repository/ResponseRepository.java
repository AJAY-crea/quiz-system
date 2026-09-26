package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Response;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponseRepository extends JpaRepository<Response, Long> {

    Optional<Response> findBySessionIdAndQuestionIdAndRollNo(Long sessionId, Long questionId, String rollNo);

    long countBySessionIdAndQuestionIdAndRollNo(Long sessionId, Long questionId, String rollNo);

    boolean existsBySessionIdAndQuestionIdAndRollNo(Long sessionId, Long questionId, String rollNo);
}
