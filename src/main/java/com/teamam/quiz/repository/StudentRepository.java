package com.teamam.quiz.repository;

import com.teamam.quiz.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for students, looked up by roll number. */
public interface StudentRepository extends JpaRepository<Student, String> {
}
