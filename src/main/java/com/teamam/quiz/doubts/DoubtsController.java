package com.teamam.quiz.doubts;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Submitting, the student queue and upvoting are open to phones; everything else is instructor-only. */
@RestController
public class DoubtsController {

    /** What a phone sends with a doubt. */
    public record DoubtRequest(String rollNo, String deviceToken, String text) {
    }

    /** What a phone sends with an upvote. */
    public record UpvoteRequest(String rollNo, String deviceToken) {
    }

    /** The dashboard's doubts panel: settings + the list. */
    public record DoubtsPanel(boolean enabled, boolean authorsVisible, boolean shared, List<DoubtView> doubts) {
    }

    private final DoubtsService doubtsService;

    public DoubtsController(DoubtsService doubtsService) {
        this.doubtsService = doubtsService;
    }

    /** Phone sends a doubt. */
    @PostMapping("/api/sessions/{sessionId}/doubts")
    public Map<String, String> submit(@PathVariable Long sessionId, @RequestBody DoubtRequest request) {
        doubtsService.submit(sessionId, request.rollNo(), request.deviceToken(), request.text());
        return Map.of("status", "sent");
    }

    /** Phone: the class's open doubts, without authors. */
    @GetMapping("/api/sessions/{sessionId}/doubts/queue")
    public DoubtQueue queue(
            @PathVariable Long sessionId, @RequestParam String rollNo, @RequestParam String deviceToken) {
        return doubtsService.queueFor(sessionId, rollNo, deviceToken);
    }

    /** Phone upvotes a doubt. */
    @PostMapping("/api/sessions/{sessionId}/doubts/{doubtId}/upvote")
    public Map<String, String> upvote(
            @PathVariable Long sessionId, @PathVariable Long doubtId, @RequestBody UpvoteRequest request) {
        doubtsService.upvote(sessionId, doubtId, request.rollNo(), request.deviceToken());
        return Map.of("status", "counted");
    }

    /** Phone takes back its upvote. */
    @PostMapping("/api/sessions/{sessionId}/doubts/{doubtId}/unvote")
    public Map<String, String> unvote(
            @PathVariable Long sessionId, @PathVariable Long doubtId, @RequestBody UpvoteRequest request) {
        doubtsService.removeUpvote(sessionId, doubtId, request.rollNo(), request.deviceToken());
        return Map.of("status", "removed");
    }

    /** Instructor: the doubts panel. */
    @GetMapping("/api/sessions/{sessionId}/doubts")
    public DoubtsPanel list(@PathVariable Long sessionId) {
        return new DoubtsPanel(
                doubtsService.isEnabled(sessionId),
                doubtsService.areAuthorsVisible(sessionId),
                doubtsService.isShared(sessionId),
                doubtsService.forInstructor(sessionId));
    }

    /** Instructor dismisses a doubt. */
    @PostMapping("/api/sessions/{sessionId}/doubts/{doubtId}/dismiss")
    public Map<String, String> dismiss(@PathVariable Long sessionId, @PathVariable Long doubtId) {
        doubtsService.dismiss(sessionId, doubtId);
        return Map.of("status", "dismissed");
    }

    /** Instructor changes the on/off or show-authors setting. */
    @PostMapping("/api/sessions/{sessionId}/doubts-settings")
    public DoubtsPanel settings(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean authorsVisible,
            @RequestParam(required = false) Boolean shared) {
        if (shared != null) {
            doubtsService.setShared(sessionId, shared);
        }
        if (enabled != null) {
            doubtsService.setEnabled(sessionId, enabled);
        }
        if (authorsVisible != null) {
            doubtsService.setAuthorsVisible(sessionId, authorsVisible);
        }
        return list(sessionId);
    }
}
