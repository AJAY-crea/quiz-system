package com.teamam.quiz.repository;

import com.teamam.quiz.domain.QuizSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {
}
