package com.teamam.quiz.export;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Instructor-only (InstructorOnlyFilter): score sheets contain every student's marks. */
@RestController
public class ExportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /** Instructor downloads the score sheet (.xlsx). */
    @GetMapping("/api/sessions/{sessionId}/export.xlsx")
    public ResponseEntity<byte[]> xlsx(@PathVariable Long sessionId) {
        return download(exportService.xlsx(sessionId), XLSX, "session-" + sessionId + "-scores.xlsx");
    }

    /** Fallback (design doc risk #5) if the .xlsx ever misbehaves on her machine. */
    @GetMapping("/api/sessions/{sessionId}/export.csv")
    public ResponseEntity<byte[]> csv(@PathVariable Long sessionId) {
        return download(exportService.csv(sessionId), new MediaType("text", "csv"), "session-" + sessionId + "-scores.csv");
    }

    /** Sends bytes as a file download with a filename. */
    private static ResponseEntity<byte[]> download(byte[] body, MediaType type, String filename) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
