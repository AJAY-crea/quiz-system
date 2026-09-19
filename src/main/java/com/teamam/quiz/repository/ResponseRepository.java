package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Response;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponseRepository extends JpaRepository<Response, Long> {
}
