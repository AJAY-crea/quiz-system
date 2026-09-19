package com.teamam.quiz.timing;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.repository.QuestionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * Server-held timing: the deadline is derived only from the server's own clock at release
 * time, never from anything the client reports. A small fixed grace window absorbs ordinary
 * network latency without giving a tampered client clock any real advantage.
 */
@Service
public class TimingService {

    public static final Duration GRACE_WINDOW = Duration.ofMillis(300);

    private final QuestionRepository questionRepository;

    public TimingService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public Instant deadlineFor(Long questionId) {
        Question question = requireQuestion(questionId);
        Instant releasedAt = question.getReleasedAt();
        if (releasedAt == null) {
            throw new IllegalStateException("question " + questionId + " has not been released yet");
        }
        return releasedAt.plusSeconds(question.getDurationSeconds());
    }

    public boolean isOnTime(Long questionId, Instant arrivedAt) {
        Instant deadlineWithGrace = deadlineFor(questionId).plus(GRACE_WINDOW);
        return !arrivedAt.isAfter(deadlineWithGrace);
    }

    private Question requireQuestion(Long questionId) {
        return questionRepository
                .findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("question not found: " + questionId));
    }
}
