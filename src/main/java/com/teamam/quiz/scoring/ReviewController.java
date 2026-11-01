package com.teamam.quiz.scoring;

import com.teamam.quiz.session.SessionEngine;
import com.teamam.quiz.session.SessionEvent;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reading and marking typed answers is instructor-only (InstructorOnlyFilter); my-marks is for phones. */
@RestController
public class ReviewController {

    /** Marks for every answer in a group of identical answers; null marks clears them. */
    public record GroupMark(String key, Double marks) {
    }

    /** Marks for one answer; null clears it. */
    public record SingleMark(Double marks) {
    }

    private final ScoringService scoringService;
    private final MyMarksService myMarksService;
    private final SessionEngine sessionEngine;

    public ReviewController(ScoringService scoringService, MyMarksService myMarksService, SessionEngine sessionEngine) {
        this.scoringService = scoringService;
        this.myMarksService = myMarksService;
        this.sessionEngine = sessionEngine;
    }

    /** Phone: this student's own marks (open to phones in InstructorOnlyFilter). */
    @GetMapping("/api/sessions/{sessionId}/my-marks")
    public MyMarks myMarks(
            @PathVariable Long sessionId, @RequestParam String rollNo, @RequestParam String deviceToken) {
        return myMarksService.forStudent(sessionId, rollNo, deviceToken);
    }

    /** Instructor publishes or hides marks; phones are told to refresh. */
    @PostMapping("/api/sessions/{sessionId}/marks-visibility")
    public Map<String, Boolean> publish(@PathVariable Long sessionId, @RequestParam boolean visible) {
        myMarksService.setPublished(sessionId, visible);
        sessionEngine.announce(sessionId, new SessionEvent(SessionEvent.MARKS, null));
        return Map.of("visible", visible);
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
