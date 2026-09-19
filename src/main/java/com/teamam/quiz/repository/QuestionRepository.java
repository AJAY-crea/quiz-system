package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySessionIdOrderByOrderIndex(Long sessionId);
}
