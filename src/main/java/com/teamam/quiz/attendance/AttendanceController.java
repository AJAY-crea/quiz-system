package com.teamam.quiz.attendance;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Web endpoint for attendance (instructor only). */
@RestController
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /** Instructor: the attendance list for a session. */
    @GetMapping("/api/sessions/{sessionId}/attendance")
    public ResponseEntity<AttendanceReport> attendance(@PathVariable Long sessionId) {
        return ResponseEntity.ok(attendanceService.derive(sessionId));
    }
}
