package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, String> {
}
