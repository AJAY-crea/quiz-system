package com.teamam.quiz.config;

import com.teamam.quiz.doubts.DoubtRefusedException;
import com.teamam.quiz.roster.JoinRefusedException;
import com.teamam.quiz.session.NotAParticipantException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns errors thrown anywhere into clear HTTP replies with a message, instead of a generic 500. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Something asked for doesn't exist (e.g. wrong session number) -> 404. */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, e);
    }

    /** Not allowed: wrong phone, expired QR, doubts switched off -> 403. */
    @ExceptionHandler({NotAParticipantException.class, JoinRefusedException.class, DoubtRefusedException.class})
    public ResponseEntity<Map<String, String>> forbidden(RuntimeException e) {
        return error(HttpStatus.FORBIDDEN, e);
    }

    /** Invalid input (e.g. an empty doubt, a bad option list) -> 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e);
    }

    /** Right request, wrong moment (e.g. releasing out of order) -> 409. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return error(HttpStatus.CONFLICT, e);
    }

    /** Builds the JSON reply {"error": message}. */
    private static ResponseEntity<Map<String, String>> error(HttpStatus status, RuntimeException e) {
        return ResponseEntity.status(status).body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
