package com.teamam.quiz.questionbank;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.session.QuestionView;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Web endpoint for adding questions (instructor). */
@RestController
@RequestMapping("/api/sessions/{sessionId}/questions")
public class QuestionController {

    private final QuestionBankService questionBankService;

    public QuestionController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    /** Instructor adds a question to a session. */
    @PostMapping
    public ResponseEntity<QuestionView> add(
            @PathVariable Long sessionId, @RequestBody AddQuestionRequest request) {
        Question question = questionBankService.addQuestion(
                sessionId,
                request.orderIndex(),
                request.type(),
                request.text(),
                request.options(),
                request.key(),
                request.durationSeconds(),
                request.maxMarks() == null ? 1 : request.maxMarks());
        return ResponseEntity.ok(QuestionView.of(question));
    }

    /** Instructor uploads a CSV of questions (format in QuestionFileParser). */
    @PostMapping("/upload")
    public Map<String, Integer> upload(@PathVariable Long sessionId, @RequestParam("file") MultipartFile file) throws IOException {
        return Map.of("added", questionBankService.addAll(sessionId, QuestionFileParser.parse(file.getInputStream())));
    }

    /** Instructor removes a question not released yet. */
    @DeleteMapping("/{questionId}")
    public Map<String, String> remove(@PathVariable Long sessionId, @PathVariable Long questionId) {
        questionBankService.remove(sessionId, questionId);
        return Map.of("status", "removed");
    }

    /** Instructor corrects a wrong answer key; existing answers are re-scored. */
    @PostMapping("/{questionId}/answer-key")
    public Map<String, String> changeAnswerKey(
            @PathVariable Long sessionId, @PathVariable Long questionId, @RequestParam List<Integer> correctOptionIndex) {
        questionBankService.changeAnswerKey(sessionId, questionId, correctOptionIndex);
        return Map.of("status", "re-scored");
    }
}
