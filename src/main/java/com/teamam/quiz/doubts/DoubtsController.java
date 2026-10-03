package com.teamam.quiz.doubts;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Only POST /doubts (submitting) is open to phones; everything else is instructor-only. */
@RestController
public class DoubtsController {

    public record DoubtRequest(String rollNo, String deviceToken, String text) {
    }

    public record DoubtsPanel(boolean enabled, boolean authorsVisible, List<DoubtView> doubts) {
    }

    private final DoubtsService doubtsService;

    public DoubtsController(DoubtsService doubtsService) {
        this.doubtsService = doubtsService;
    }

    @PostMapping("/api/sessions/{sessionId}/doubts")
    public Map<String, String> submit(@PathVariable Long sessionId, @RequestBody DoubtRequest request) {
        doubtsService.submit(sessionId, request.rollNo(), request.deviceToken(), request.text());
        return Map.of("status", "sent");
    }

    @GetMapping("/api/sessions/{sessionId}/doubts")
    public DoubtsPanel list(@PathVariable Long sessionId) {
        return new DoubtsPanel(
                doubtsService.isEnabled(sessionId),
                doubtsService.areAuthorsVisible(sessionId),
                doubtsService.forInstructor(sessionId));
    }

    @PostMapping("/api/sessions/{sessionId}/doubts/{doubtId}/dismiss")
    public Map<String, String> dismiss(@PathVariable Long sessionId, @PathVariable Long doubtId) {
        doubtsService.dismiss(sessionId, doubtId);
        return Map.of("status", "dismissed");
    }

    @PostMapping("/api/sessions/{sessionId}/doubts-settings")
    public DoubtsPanel settings(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Boolean authorsVisible) {
        if (enabled != null) {
            doubtsService.setEnabled(sessionId, enabled);
        }
        if (authorsVisible != null) {
            doubtsService.setAuthorsVisible(sessionId, authorsVisible);
        }
        return list(sessionId);
    }
}
