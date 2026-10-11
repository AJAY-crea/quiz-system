package com.teamam.quiz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** Turns on the live connection (WebSocket + STOMP) so the server can push to phones instantly. */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /** Sets up the message channels: /topic = to everyone, /queue = to one phone, /app = phone to server. */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic = broadcast to the whole class; /queue = per-student messages such as answer acks.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    /** The address phones connect to (/ws, or /ws/websocket for a plain WebSocket). */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
        // Otherwise one client's SUBSCRIBE and the SEND right after it can be handled by different
        // threads out of order, and the ack for that SEND can arrive before the subscription exists.
        registry.setPreserveReceiveOrder(true);
    }
}
