package com.teamam.quiz.questionbank;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.session.QuestionView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        Question question = questionBankService.add(
                sessionId,
                request.orderIndex(),
                request.type(),
                request.text(),
                request.options(),
                request.correctOptionIndex(),
                request.durationSeconds(),
                request.maxMarks() == null ? 1 : request.maxMarks());
        return ResponseEntity.ok(QuestionView.of(question));
    }
}
