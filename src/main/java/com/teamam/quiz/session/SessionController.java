package com.teamam.quiz.session;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionEngine sessionEngine;

    public SessionController(SessionEngine sessionEngine) {
        this.sessionEngine = sessionEngine;
    }

    @PostMapping
    public ResponseEntity<SessionView> create(@RequestBody CreateSessionRequest request) {
        QuizSession session = sessionEngine.createSession(request.title());
        return ResponseEntity.ok(SessionView.of(session));
    }

    @PostMapping("/{sessionId}/questions/{questionId}/release")
    public ResponseEntity<QuestionView> release(
            @PathVariable Long sessionId, @PathVariable Long questionId) {
        Question question = sessionEngine.release(sessionId, questionId);
        return ResponseEntity.ok(QuestionView.of(question));
    }
}
