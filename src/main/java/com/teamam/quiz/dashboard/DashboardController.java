package com.teamam.quiz.dashboard;

import com.teamam.quiz.domain.Participant;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
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
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module 13: a thin composition of the other modules for the instructor's screen. It only reads
 * and assembles; every action (release, extend, open joining...) goes to its owning module's endpoint.
 * Instructor-only via InstructorOnlyFilter.
 */
@RestController
public class DashboardController {

    /**
     * Optional public link (e.g. a Cloudflare Tunnel https://….trycloudflare.com) so phones on mobile
     * data can join; set with --quiz.public-url=..., pasted on the dashboard, or read from a running
     * cloudflared, and offered first in the address list.
     */
    private volatile String publicUrl;

    /** Where a quick tunnel started with "--metrics 127.0.0.1:20241" reports its own link. */
    private final String tunnelInfoUrl;

    private static final HttpClient TUNNEL_INFO = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(300)).build();
    private static final Pattern TUNNEL_HOSTNAME = Pattern.compile("\"hostname\"\\s*:\\s*\"([A-Za-z0-9.\\-]+)\"");

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
            TimingService timingService,
            @Value("${quiz.public-url:}") String publicUrl,
            @Value("${quiz.tunnel-info-url:http://127.0.0.1:20241/quicktunnel}") String tunnelInfoUrl) {
        this.tunnelInfoUrl = tunnelInfoUrl;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.participantRepository = participantRepository;
        this.responseRepository = responseRepository;
        this.joinWindowService = joinWindowService;
        this.timingService = timingService;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    /** All sessions, newest first, for the dashboard's session picker. */
    @GetMapping("/api/dashboard/sessions")
    public List<SessionView> sessions() {
        return sessionRepository.findAll().stream()
                .sorted(Comparator.comparing(QuizSession::getId).reversed())
                .map(SessionView::of)
                .toList();
    }

    /** Everything the dashboard shows for a session, assembled from the other modules. */
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

    /** The join QR image for the current code, pointing at the chosen classroom address. */
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
            if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || isVirtualMachineAdapter(nic.getDisplayName())) {
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
        // The first entry becomes the default, so put the likeliest classroom network first.
        addresses.sort(Comparator.comparingInt(a -> classroomRank(a.get("adapter"), a.get("url"))));
        String internet = PLAIN_BASE_URL.matcher(publicUrl).matches() ? publicUrl : runningTunnelLink();
        if (internet != null) {
            addresses.add(0, Map.of("url", internet, "adapter", "Internet link: works on mobile data"));
        }
        return addresses;
    }

    /** Instructor pastes (or clears, with an empty value) the internet link without restarting the app. */
    @PostMapping("/api/dashboard/public-url")
    public Map<String, String> setPublicUrl(@RequestParam String url) {
        String trimmed = url.trim().replaceAll("/+$", "");
        if (!trimmed.isEmpty() && !(trimmed.startsWith("https://") && PLAIN_BASE_URL.matcher(trimmed).matches())) {
            throw new IllegalArgumentException("Paste the https:// link that cloudflared printed, e.g. https://some-words.trycloudflare.com");
        }
        publicUrl = trimmed;
        return Map.of("publicUrl", trimmed);
    }

    /** The link of a quick tunnel running on this laptop, if cloudflared reports one; null otherwise. */
    private String runningTunnelLink() {
        try {
            HttpResponse<String> info = TUNNEL_INFO.send(
                    HttpRequest.newBuilder(URI.create(tunnelInfoUrl)).timeout(Duration.ofMillis(500)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Matcher hostname = TUNNEL_HOSTNAME.matcher(info.body());
            return info.statusCode() == 200 && hostname.find() ? "https://" + hostname.group(1) : null;
        } catch (IOException | IllegalArgumentException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Adapters made by VM/container software (VirtualBox, VMware, Hyper-V, WSL): phones can never reach them. */
    static boolean isVirtualMachineAdapter(String adapterName) {
        String name = adapterName == null ? "" : adapterName.toLowerCase();
        return name.contains("virtualbox") || name.contains("vmware") || name.contains("hyper-v")
                || name.contains("vethernet") || name.contains("wsl") || name.contains("docker");
    }

    /** Lower = listed first: the laptop's own hotspot, then Wi-Fi, then anything else (e.g. a cable). */
    static int classroomRank(String adapterName, String url) {
        String name = adapterName == null ? "" : adapterName.toLowerCase();
        if (name.contains("wi-fi direct") || url.contains("//192.168.137.")) {
            return 0;
        }
        if (name.contains("wi-fi") || name.contains("wifi") || name.contains("wireless") || name.contains("802.11")) {
            return 1;
        }
        return 2;
    }

    /** One row of the live-quiz table: state, answers so far, time left if open. */
    private DashboardView.QuestionRow questionRow(QuizSession session, Question q) {
        boolean released = q.getReleasedAt() != null;
        Long remainingMs = null;
        if (released && q.getOrderIndex() == session.getCurrentQuestionOrder()) {
            remainingMs = Math.max(0, Duration.between(Instant.now(), timingService.deadlineFor(q.getId())).toMillis());
        }
        long awaitingMarks = q.getType() == QuestionType.TYPED
                ? responseRepository.countBySessionIdAndQuestionIdAndOnTimeTrueAndAwardedMarksIsNull(session.getId(), q.getId())
                : 0;
        return new DashboardView.QuestionRow(
                q.getId(), q.getOrderIndex(), q.getType().name(), q.getText(), List.copyOf(q.getOptions()),
                q.getCorrectOptionIndex(), q.getDurationSeconds() + q.getExtensionSeconds(), released,
                responseRepository.countBySessionIdAndQuestionId(session.getId(), q.getId()), remainingMs,
                q.getMaxMarks(), awaitingMarks);
    }
}
