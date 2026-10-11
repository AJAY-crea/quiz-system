package com.teamam.quiz.repository;

import com.teamam.quiz.domain.QuizSession;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for sessions (save, find by id). Spring writes the SQL. */
public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {
}
