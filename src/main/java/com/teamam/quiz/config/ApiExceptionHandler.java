package com.teamam.quiz.config;

import com.teamam.quiz.session.NotAParticipantException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(NotAParticipantException.class)
    public ResponseEntity<Map<String, String>> forbidden(NotAParticipantException e) {
        return error(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, e);
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, RuntimeException e) {
        return ResponseEntity.status(status).body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
