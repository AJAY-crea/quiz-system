package com.teamam.quiz.integrity;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Web endpoints: phones send signals; the instructor reads the summary. */
@RestController
public class IntegrityController {

    private final IntegrityService integrityService;

    public IntegrityController(IntegrityService integrityService) {
        this.integrityService = integrityService;
    }

    /** Student page posts here with navigator.sendBeacon, which survives the page being hidden. */
    @PostMapping("/api/sessions/{sessionId}/integrity")
    public Map<String, String> report(@PathVariable Long sessionId, @RequestBody IntegrityReport report) {
        return Map.of("outcome", integrityService.record(sessionId, report).name());
    }

    /** Instructor-only (InstructorOnlyFilter). */
    @GetMapping("/api/sessions/{sessionId}/integrity")
    public List<IntegritySummaryRow> summary(@PathVariable Long sessionId) {
        return integrityService.summary(sessionId);
    }
}
