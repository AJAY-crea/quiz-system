package com.teamam.quiz.roster;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/roster")
public class RosterController {

    private final RosterService rosterService;

    public RosterController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    @PostMapping("/{sessionId}/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @PathVariable Long sessionId, @RequestParam("file") MultipartFile file) throws IOException {
        List<String> rollNumbers = RosterFileParser.parseRollNumbers(file);
        rosterService.uploadRoster(sessionId, rollNumbers);
        return ResponseEntity.ok(Map.of("sessionId", sessionId, "rosterSize", rollNumbers.size()));
    }

    @PostMapping("/{sessionId}/join")
    public ResponseEntity<JoinResult> join(@PathVariable Long sessionId, @RequestBody JoinRequest request) {
        JoinResult result = rosterService.join(sessionId, request.rollNo(), request.deviceToken(), request.ticket());
        boolean rejected = result.outcome() == JoinOutcome.REJECTED_UNLISTED
                || result.outcome() == JoinOutcome.REJECTED_INVALID_TICKET;
        HttpStatus status = rejected ? HttpStatus.FORBIDDEN : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}
