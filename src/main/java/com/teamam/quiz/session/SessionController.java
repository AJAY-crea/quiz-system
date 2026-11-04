package com.teamam.quiz.session;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.timing.TimingService;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Web endpoints for sessions: create, release, extend, close, end (instructor) and state (phones). */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionEngine sessionEngine;
    private final SessionStateService sessionStateService;
    private final TimingService timingService;

    public SessionController(
            SessionEngine sessionEngine, SessionStateService sessionStateService, TimingService timingService) {
        this.sessionEngine = sessionEngine;
        this.sessionStateService = sessionStateService;
        this.timingService = timingService;
    }

    /** Instructor creates a session. */
    @PostMapping
    public ResponseEntity<SessionView> create(@RequestBody CreateSessionRequest request) {
        QuizSession session = sessionEngine.createSession(request.title());
        return ResponseEntity.ok(SessionView.of(session));
    }

    /** Instructor releases the next question; every phone is told instantly. */
    @PostMapping("/{sessionId}/questions/{questionId}/release")
    public ResponseEntity<QuestionView> release(
            @PathVariable Long sessionId, @PathVariable Long questionId) {
        Question question = sessionEngine.release(sessionId, questionId);
        return ResponseEntity.ok(QuestionView.of(question));
    }

    /** Instructor gives the open question extra seconds; phones refresh their timer. */
    @PostMapping("/{sessionId}/questions/{questionId}/extend")
    public ResponseEntity<Map<String, Object>> extend(
            @PathVariable Long sessionId, @PathVariable Long questionId, @RequestParam int seconds) {
        Instant deadline = timingService.extendDeadline(questionId, seconds);
        sessionEngine.announce(sessionId, new SessionEvent(SessionEvent.EXTENDED, questionId));
        return ResponseEntity.ok(Map.of("questionId", questionId, "deadline", deadline.toString()));
    }

    /** Instructor closes the open question early, with a few seconds' notice; phones refresh their timer. */
    @PostMapping("/{sessionId}/questions/{questionId}/close")
    public ResponseEntity<Map<String, Object>> close(@PathVariable Long sessionId, @PathVariable Long questionId) {
        Instant deadline = timingService.closeSoon(questionId);
        sessionEngine.announce(sessionId, new SessionEvent(SessionEvent.CLOSING, questionId));
        return ResponseEntity.ok(Map.of("questionId", questionId, "deadline", deadline.toString()));
    }

    /** Instructor makes an earlier question live again (only once the live one's time is over). */
    @PostMapping("/{sessionId}/questions/{questionId}/reopen")
    public ResponseEntity<QuestionView> reopen(
            @PathVariable Long sessionId, @PathVariable Long questionId, @RequestParam(required = false) Integer seconds) {
        return ResponseEntity.ok(QuestionView.of(sessionEngine.reopen(sessionId, questionId, seconds)));
    }

    /** Instructor ends the session. */
    @PostMapping("/{sessionId}/end")
    public ResponseEntity<Map<String, String>> end(@PathVariable Long sessionId) {
        sessionEngine.end(sessionId);
        return ResponseEntity.ok(Map.of("status", "ended"));
    }

    /** Phone asks "what should I show now?" after joining, reconnecting or a broadcast. */
    @GetMapping("/{sessionId}/state")
    public ResponseEntity<SessionStateView> state(
            @PathVariable Long sessionId, @RequestParam String rollNo, @RequestParam String deviceToken) {
        return ResponseEntity.ok(sessionStateService.currentState(sessionId, rollNo, deviceToken));
    }
}
