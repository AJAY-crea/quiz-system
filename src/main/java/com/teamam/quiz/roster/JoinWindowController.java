package com.teamam.quiz.roster;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Everything here except join-ticket is instructor-only (see InstructorOnlyFilter). */
@RestController
@RequestMapping("/api/sessions/{sessionId}")
public class JoinWindowController {

    private final JoinWindowService joinWindowService;

    public JoinWindowController(JoinWindowService joinWindowService) {
        this.joinWindowService = joinWindowService;
    }

    /** Instructor: open joining for N seconds. */
    @PostMapping("/join-window/open")
    public ResponseEntity<JoinWindowStatus> open(@PathVariable Long sessionId, @RequestParam int seconds) {
        return ResponseEntity.ok(joinWindowService.open(sessionId, seconds));
    }

    /** Instructor: close joining. */
    @PostMapping("/join-window/close")
    public ResponseEntity<JoinWindowStatus> close(@PathVariable Long sessionId) {
        return ResponseEntity.ok(joinWindowService.close(sessionId));
    }

    /** Instructor: current joining status and QR code. */
    @GetMapping("/join-window")
    public ResponseEntity<JoinWindowStatus> status(@PathVariable Long sessionId) {
        return ResponseEntity.ok(joinWindowService.status(sessionId));
    }

    /** Phone: swap a just-scanned QR code for a 2-minute join ticket. */
    @PostMapping("/join-ticket")
    public ResponseEntity<JoinTicket> ticket(@PathVariable Long sessionId, @RequestParam String code) {
        return ResponseEntity.ok(joinWindowService.issueTicket(sessionId, code));
    }
}
