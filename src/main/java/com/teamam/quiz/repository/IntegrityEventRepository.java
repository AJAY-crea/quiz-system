package com.teamam.quiz.repository;

import com.teamam.quiz.domain.IntegrityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrityEventRepository extends JpaRepository<IntegrityEvent, Long> {
}
