package com.teamam.quiz.resilience;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.repository.ResponseRepository;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnswerOverStompTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private ResponseRepository responses;

    // Guards against: acks never reaching the sender, since students have no login (no Principal).
    @Test
    void studentReceivesOwnAckOverStomp() throws Exception {
        String rollNo = "CS23B301";
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, rollNo);

        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        List<Throwable> clientErrors = new CopyOnWriteArrayList<>();
        StompSession session = client
                .connectAsync("ws://localhost:" + port + "/ws/websocket", new StompSessionHandlerAdapter() {
                    @Override
                    public void handleException(
                            StompSession s, StompCommand command, StompHeaders headers, byte[] payload, Throwable e) {
                        clientErrors.add(e);
                    }

                    @Override
                    public void handleTransportError(StompSession s, Throwable e) {
                        clientErrors.add(e);
                    }
                })
                .get(5, SECONDS);
        try {
            BlockingQueue<AcceptResult> acks = new LinkedBlockingQueue<>();
            session.subscribe("/user/queue/answer-ack", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return AcceptResult.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    acks.add((AcceptResult) payload);
                }
            });

            session.send("/app/session/" + quiz.sessionId + "/answer", quiz.answer("sub-s1", rollNo, 0));

            AcceptResult ack = acks.poll(5, SECONDS);
            assertThat(clientErrors).isEmpty();
            assertThat(ack)
                    .as("ack; answer stored on server = %s",
                            responses.existsBySessionIdAndQuestionIdAndRollNo(quiz.sessionId, quiz.questionId, rollNo))
                    .isNotNull();
            assertThat(ack.submissionId()).isEqualTo("sub-s1");
            assertThat(ack.outcome()).isEqualTo(AcceptOutcome.ACCEPTED);
        } finally {
            session.disconnect();
            client.stop();
        }
    }
}
