package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Question;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for questions; method names like findBy... become queries automatically. */
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySessionIdOrderByOrderIndex(Long sessionId);

    Optional<Question> findBySessionIdAndOrderIndex(Long sessionId, int orderIndex);
}
