package com.teamam.quiz.dashboard;

import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.ParticipantRepository;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.roster.JoinWindowService;
import com.teamam.quiz.roster.JoinWindowStatus;
import com.teamam.quiz.session.SessionView;
import com.teamam.quiz.timing.TimingService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module 13: a thin composition of the other modules for the instructor's screen. It only reads
 * and assembles; every action (release, extend, open joining...) goes to its owning module's endpoint.
 * Instructor-only via InstructorOnlyFilter.
 */
@RestController
public class DashboardController {

    private static final Pattern PLAIN_BASE_URL = Pattern.compile("https?://[A-Za-z0-9.\\-]+(:\\d{1,5})?");

    private final QuizSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final ParticipantRepository participantRepository;
    private final ResponseRepository responseRepository;
    private final JoinWindowService joinWindowService;
    private final TimingService timingService;

    public DashboardController(
            QuizSessionRepository sessionRepository,
            QuestionRepository questionRepository,
            ParticipantRepository participantRepository,
            ResponseRepository responseRepository,
            JoinWindowService joinWindowService,
            TimingService timingService) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.participantRepository = participantRepository;
        this.responseRepository = responseRepository;
        this.joinWindowService = joinWindowService;
        this.timingService = timingService;
    }

    @GetMapping("/api/dashboard/sessions")
    public List<SessionView> sessions() {
        return sessionRepository.findAll().stream()
                .sorted(Comparator.comparing(QuizSession::getId).reversed())
                .map(SessionView::of)
                .toList();
    }

    @GetMapping("/api/sessions/{sessionId}/dashboard")
    @Transactional(readOnly = true)
    public DashboardView dashboard(@PathVariable Long sessionId) {
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));

        List<Participant> participants = participantRepository.findBySessionId(sessionId);
        List<DashboardView.QuestionRow> questions = questionRepository.findBySessionIdOrderByOrderIndex(sessionId)
                .stream()
                .map(q -> questionRow(session, q))
                .toList();

        return new DashboardView(
                SessionView.of(session),
                session.getRosterRollNumbers().size(),
                participants.size(),
                participants.stream().filter(Participant::isFlagged).count(),
                questions,
                joinWindowService.status(sessionId));
    }

    @GetMapping(value = "/api/sessions/{sessionId}/join-qr.svg")
    public ResponseEntity<String> joinQr(@PathVariable Long sessionId, @RequestParam String base) {
        if (!PLAIN_BASE_URL.matcher(base).matches()) {
            throw new IllegalArgumentException("base must look like http://192.168.1.5:8080");
        }
        JoinWindowStatus join = joinWindowService.status(sessionId);
        if (!join.open()) {
            throw new IllegalStateException("joining is closed");
        }
        String url = base + "/student.html?s=" + sessionId + "&c=" + join.code();
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .cacheControl(CacheControl.noStore())
                .body(QrSvg.svg(QrSvg.matrix(url)));
    }

    /**
     * Candidate classroom addresses of this laptop. Laptops often have several adapters (Wi-Fi,
     * VPN, VirtualBox...), so the instructor picks the right one on screen.
     */
    @GetMapping("/api/dashboard/host-addresses")
    public List<Map<String, String>> hostAddresses(HttpServletRequest request) throws SocketException {
        int port = request.getLocalPort();
        List<Map<String, String>> addresses = new ArrayList<>();
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                continue;
            }
            for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                    addresses.add(Map.of(
                            "url", "http://" + address.getHostAddress() + ":" + port,
                            "adapter", nic.getDisplayName()));
                }
            }
        }
        return addresses;
    }

    private DashboardView.QuestionRow questionRow(QuizSession session, Question q) {
        boolean released = q.getReleasedAt() != null;
        Long remainingMs = null;
        if (released && q.getOrderIndex() == session.getCurrentQuestionOrder()) {
            remainingMs = Math.max(0, Duration.between(Instant.now(), timingService.deadlineFor(q.getId())).toMillis());
        }
        return new DashboardView.QuestionRow(
                q.getId(), q.getOrderIndex(), q.getType().name(), q.getText(), List.copyOf(q.getOptions()),
                q.getCorrectOptionIndex(), q.getDurationSeconds() + q.getExtensionSeconds(), released,
                responseRepository.countBySessionIdAndQuestionId(session.getId(), q.getId()), remainingMs);
    }
}
