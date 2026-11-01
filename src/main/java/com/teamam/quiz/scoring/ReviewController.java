package com.teamam.quiz.scoring;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Instructor-only (InstructorOnlyFilter): reading and marking typed answers. */
@RestController
public class ReviewController {

    /** Marks for every answer in a group of identical answers; null marks clears them. */
    public record GroupMark(String key, Double marks) {
    }

    /** Marks for one answer; null clears it. */
    public record SingleMark(Double marks) {
    }

    private final ScoringService scoringService;

    public ReviewController(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    /** Instructor: a typed question's answers, grouped for marking. */
    @GetMapping("/api/sessions/{sessionId}/questions/{questionId}/review")
    public TypedReview review(@PathVariable Long sessionId, @PathVariable Long questionId) {
        return scoringService.groupTyped(sessionId, questionId);
    }

    /** Instructor marks a whole group at once. */
    @PostMapping("/api/sessions/{sessionId}/questions/{questionId}/review/group")
    public Map<String, Integer> markGroup(
            @PathVariable Long sessionId, @PathVariable Long questionId, @RequestBody GroupMark mark) {
        return Map.of("updated", scoringService.markGroup(sessionId, questionId, mark.key(), mark.marks()));
    }

    /** Instructor marks one answer differently from its group. */
    @PostMapping("/api/sessions/{sessionId}/responses/{responseId}/marks")
    public Map<String, String> markOne(
            @PathVariable Long sessionId, @PathVariable Long responseId, @RequestBody SingleMark mark) {
        scoringService.markOne(sessionId, responseId, mark.marks());
        return Map.of("status", "saved");
    }
}
