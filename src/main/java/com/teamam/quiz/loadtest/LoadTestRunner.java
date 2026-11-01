package com.teamam.quiz.loadtest;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.resilience.AcceptOutcome;
import com.teamam.quiz.resilience.AcceptResult;
import com.teamam.quiz.resilience.AnswerSubmission;
import com.teamam.quiz.roster.JoinWindowService;
import com.teamam.quiz.roster.RosterService;
import com.teamam.quiz.session.SessionEngine;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Module 12, dev-only and not on the dashboard. One simulated phone per rostered student: joins over
 * HTTP, opens its own WebSocket, and all of them answer at the same moment, each sending its answer
 * twice as a phone resending after a dropped reply would. Limit: everything runs on this one laptop, so it
 * loads the server and database but not the classroom Wi-Fi.
 */
@Component
public class LoadTestRunner {

    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(60);
    private static final int CONNECT_THREADS = 16;

    private final QuizSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final ResponseRepository responseRepository;
    private final QuestionBankService questionBank;
    private final RosterService rosterService;
    private final JoinWindowService joinWindow;
    private final SessionEngine sessionEngine;
    private final Environment environment;

    public LoadTestRunner(
            QuizSessionRepository sessionRepository,
            QuestionRepository questionRepository,
            ResponseRepository responseRepository,
            QuestionBankService questionBank,
            RosterService rosterService,
            JoinWindowService joinWindow,
            SessionEngine sessionEngine,
            Environment environment) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
        this.questionBank = questionBank;
        this.rosterService = rosterService;
        this.joinWindow = joinWindow;
        this.sessionEngine = sessionEngine;
        this.environment = environment;
    }

    /** Makes a throwaway session with a synthetic roster and one open question, then runs against it. */
    public LoadReport runFresh(int clientCount) throws InterruptedException {
        QuizSession session = sessionEngine.createSession("Load test " + clientCount);
        List<String> roster = new ArrayList<>();
        for (int i = 1; i <= clientCount; i++) {
            roster.add(String.format("LOAD%05d", i));
        }
        rosterService.uploadRoster(session.getId(), roster);
        Question question = questionBank.add(
                session.getId(), 1, QuestionType.MCQ, "Load test", List.of("A", "B", "C", "D"), 0, 600);
        sessionEngine.release(session.getId(), question.getId());
        return run(session.getId(), clientCount);
    }

    /** Runs clientCount simulated phones against this session's roster and its currently open question. */
    public LoadReport run(Long sessionId, int clientCount) throws InterruptedException {
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
        Long questionId = questionRepository
                .findBySessionIdAndOrderIndex(sessionId, session.getCurrentQuestionOrder())
                .orElseThrow(() -> new IllegalStateException("release a question before running the load test"))
                .getId();
        List<String> rollNos = new ArrayList<>(session.getRosterRollNumbers());
        Collections.sort(rollNos);
        rollNos = rollNos.subList(0, Math.min(clientCount, rollNos.size()));

        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        List<String> errors = new CopyOnWriteArrayList<>();
        int joined = joinAll(port, sessionId, rollNos, errors);

        ExecutorService pool = Executors.newFixedThreadPool(CONNECT_THREADS);
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new MappingJackson2MessageConverter());
        stomp.setInboundMessageSizeLimit(64 * 1024);
        Map<String, Client> clients = new ConcurrentHashMap<>();
        CountDownLatch connecting = new CountDownLatch(rollNos.size());
        for (String rollNo : rollNos) {
            pool.submit(() -> {
                try {
                    clients.put(rollNo, connect(stomp, port, rollNo));
                } catch (Exception e) {
                    errors.add("connect " + rollNo + ": " + e.getMessage());
                } finally {
                    connecting.countDown();
                }
            });
        }
        connecting.await(ACK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        pool.shutdown();
        // A subscribe travels on its own frame: give them a moment to land so no first reply is missed.
        Thread.sleep(500);

        // The whole class presses Submit at once.
        long started = System.nanoTime();
        List<Thread> senders = new ArrayList<>();
        CountDownLatch go = new CountDownLatch(1);
        for (Client client : clients.values()) {
            Thread sender = new Thread(() -> {
                try {
                    go.await();
                    client.sendTwice(sessionId, questionId);
                } catch (Exception e) {
                    errors.add("send " + client.rollNo + ": " + e.getMessage());
                }
            });
            sender.start();
            senders.add(sender);
        }
        go.countDown();
        for (Thread sender : senders) {
            sender.join();
        }
        long deadline = System.nanoTime() + ACK_TIMEOUT.toNanos();
        for (Client client : clients.values()) {
            client.awaitAcks(Math.max(0, deadline - System.nanoTime()));
        }
        long totalMs = (System.nanoTime() - started) / 1_000_000;

        LoadReport report = report(
                sessionId, questionId, rollNos.size(), joined, clients, totalMs, errors);
        clients.values().forEach(Client::disconnect);
        stomp.stop();
        return report;
    }

    /** Joins every student over HTTP, the way a phone does after scanning the QR. Returns how many got in. */
    private int joinAll(int port, Long sessionId, List<String> rollNos, List<String> errors) throws InterruptedException {
        joinWindow.open(sessionId, 600);
        String ticket = joinWindow.issueTicket(sessionId, joinWindow.status(sessionId).code()).ticket();
        HttpClient http = HttpClient.newHttpClient();
        int joined = 0;
        for (String rollNo : rollNos) {
            String body = "{\"rollNo\":\"" + rollNo + "\",\"deviceToken\":\"" + deviceOf(rollNo) + "\",\"ticket\":\"" + ticket + "\"}";
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/roster/" + sessionId + "/join"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    joined++;
                } else {
                    errors.add("join " + rollNo + ": HTTP " + response.statusCode());
                }
            } catch (java.io.IOException e) {
                errors.add("join " + rollNo + ": " + e.getMessage());
            }
        }
        return joined;
    }

    /** One simulated phone's connection, already listening for its acks. */
    private Client connect(WebSocketStompClient stomp, int port, String rollNo) throws Exception {
        StompSession session = stomp
                .connectAsync("ws://localhost:" + port + "/ws/websocket", new StompSessionHandlerAdapter() {})
                .get(ACK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        Client client = new Client(rollNo, session);
        session.subscribe("/user/queue/answer-ack", client);
        return client;
    }

    /** Adds up every client's replies and checks the database. */
    private LoadReport report(
            Long sessionId, Long questionId, int clientCount, int joined, Map<String, Client> clients,
            long totalMs, List<String> errors) {
        int onTime = 0;
        int late = 0;
        int duplicates = 0;
        int rejected = 0;
        int noAck = 0;
        List<Long> latencies = new ArrayList<>();
        for (Client client : clients.values()) {
            if (client.acks.size() < 2) {
                noAck++;
            }
            for (AcceptResult ack : client.acks) {
                if (ack.outcome() == AcceptOutcome.ACCEPTED) {
                    if (ack.onTime()) {
                        onTime++;
                    } else {
                        late++;
                    }
                } else if (ack.outcome() == AcceptOutcome.DUPLICATE) {
                    duplicates++;
                } else {
                    rejected++;
                    errors.add("rejected " + client.rollNo + ": " + ack.reason());
                }
            }
            if (client.firstAckMs >= 0) {
                latencies.add(client.firstAckMs);
            }
        }
        Collections.sort(latencies);
        return new LoadReport(
                clientCount, joined, clients.size(), onTime, late, duplicates, rejected, noAck,
                responseRepository.countBySessionIdAndQuestionId(sessionId, questionId),
                percentile(latencies, 50), percentile(latencies, 95),
                latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1),
                totalMs, List.copyOf(errors.subList(0, Math.min(errors.size(), 10))));
    }

    /** The value below which this percentage of the sorted list falls. */
    private static long percentile(List<Long> sorted, int percent) {
        if (sorted.isEmpty()) {
            return 0;
        }
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * percent / 100.0) - 1));
    }

    private static String deviceOf(String rollNo) {
        return "load-" + rollNo;
    }

    /** A simulated phone: sends the same answer twice and collects its replies. */
    private static final class Client implements StompFrameHandler {

        final String rollNo;
        final StompSession session;
        final List<AcceptResult> acks = new CopyOnWriteArrayList<>();
        final CountDownLatch bothAcks = new CountDownLatch(2);
        volatile long sentAt;
        volatile long firstAckMs = -1;

        Client(String rollNo, StompSession session) {
            this.rollNo = rollNo;
            this.session = session;
        }

        /** Sends the answer, then the same submission again. */
        void sendTwice(Long sessionId, Long questionId) {
            AnswerSubmission answer = new AnswerSubmission(
                    "load-" + rollNo, rollNo, deviceOf(rollNo), questionId, 0, null);
            sentAt = System.nanoTime();
            session.send("/app/session/" + sessionId + "/answer", answer);
            session.send("/app/session/" + sessionId + "/answer", answer);
        }

        /** Waits for both replies, up to the time left. */
        void awaitAcks(long nanos) throws InterruptedException {
            bothAcks.await(nanos, TimeUnit.NANOSECONDS);
        }

        void disconnect() {
            if (session.isConnected()) {
                session.disconnect();
            }
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return AcceptResult.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if (firstAckMs < 0) {
                firstAckMs = (System.nanoTime() - sentAt) / 1_000_000;
            }
            acks.add((AcceptResult) payload);
            bothAcks.countDown();
        }
    }
}
