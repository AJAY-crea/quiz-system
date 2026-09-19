package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Doubt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoubtRepository extends JpaRepository<Doubt, Long> {
}
