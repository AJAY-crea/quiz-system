package com.teamam.quiz.session;

import com.teamam.quiz.resilience.AcceptResult;
import com.teamam.quiz.resilience.AnswerSubmission;
import com.teamam.quiz.resilience.ResilienceService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class LiveController {

    private final ResilienceService resilienceService;

    public LiveController(ResilienceService resilienceService) {
        this.resilienceService = resilienceService;
    }

    /**
     * Students have no login, so there is no Principal: @SendToUser falls back to the sender's
     * own WebSocket session, and only that browser receives the ack.
     */
    @MessageMapping("/session/{sessionId}/answer")
    @SendToUser(destinations = "/queue/answer-ack", broadcast = false)
    public AcceptResult answer(@DestinationVariable Long sessionId, AnswerSubmission submission) {
        return resilienceService.accept(sessionId, submission);
    }

    @MessageMapping("/session/{sessionId}/ping")
    @SendTo("/topic/session/{sessionId}/pong")
    public String ping(@DestinationVariable Long sessionId, String payload) {
        return payload;
    }
}
