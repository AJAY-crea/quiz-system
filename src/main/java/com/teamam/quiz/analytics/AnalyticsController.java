package com.teamam.quiz.analytics;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Instructor-only (InstructorOnlyFilter): analytics reveal which answers are right. */
@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** Instructor: how each released question of this session went. */
    @GetMapping("/api/sessions/{sessionId}/analytics")
    public List<QuestionAnalytics> forSession(@PathVariable Long sessionId) {
        return analyticsService.forSession(sessionId);
    }

    /** Instructor: one line per session, across the semester. */
    @GetMapping("/api/analytics/semester")
    public List<SessionSummary> semester() {
        return analyticsService.semester();
    }
}
