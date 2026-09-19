package com.teamam.quiz.session;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

/**
 * STOMP wiring proof-of-life: a client subscribed to /topic/session/{id}/pong gets back
 * whatever it sends to /app/session/{id}/ping. Question push itself goes through
 * SessionEngine.release via SimpMessagingTemplate, not through this controller.
 */
@Controller
public class LiveController {

    @MessageMapping("/session/{sessionId}/ping")
    @SendTo("/topic/session/{sessionId}/pong")
    public String ping(@DestinationVariable Long sessionId, String payload) {
        return payload;
    }
}
