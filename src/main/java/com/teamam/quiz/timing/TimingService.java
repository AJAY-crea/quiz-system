package com.teamam.quiz.timing;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.repository.QuestionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-held timing: the deadline is derived only from the server's own clock at release
 * time, never from anything the client reports. A small fixed grace window absorbs ordinary
 * network latency without giving a tampered client clock any real advantage.
 */
@Service
public class TimingService {

    public static final Duration GRACE_WINDOW = Duration.ofMillis(300);
    public static final int MAX_EXTENSION_SECONDS = 600;
    public static final Duration CLOSE_NOTICE = Duration.ofSeconds(5);

    private final QuestionRepository questionRepository;

    public TimingService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    /** When this question closes: released time + duration + any extension. */
    public Instant deadlineFor(Long questionId) {
        return deadlineOf(requireQuestion(questionId));
    }

    /** Did an answer arrive by the deadline + 300 ms grace? Uses the server clock only. */
    public boolean isOnTime(Long questionId, Instant arrivedAt) {
        Instant deadlineWithGrace = deadlineFor(questionId).plus(GRACE_WINDOW);
        return !arrivedAt.isAfter(deadlineWithGrace);
    }

    /** Instructor action: give a released question extra time (e.g. after the app was down). */
    @Transactional
    public Instant extendDeadline(Long questionId, int seconds) {
        if (seconds <= 0 || seconds > MAX_EXTENSION_SECONDS) {
            throw new IllegalArgumentException(
                    "extension must be between 1 and " + MAX_EXTENSION_SECONDS + " seconds");
        }
        Question question = requireQuestion(questionId);
        if (question.getReleasedAt() == null) {
            throw new IllegalStateException("cannot extend question " + questionId + " before it is released");
        }
        question.setExtensionSeconds(question.getExtensionSeconds() + seconds);
        questionRepository.save(question);
        return deadlineOf(question);
    }

    /**
     * Instructor action: everyone has answered, so end the question early. It closes a few seconds
     * from now, not at once, so phones can still send what students have typed. Stored as a negative
     * extension, so restart recovery and on-time checks need no special case.
     */
    @Transactional
    public Instant closeSoon(Long questionId) {
        Question question = requireQuestion(questionId);
        Instant now = Instant.now();
        if (question.getReleasedAt() == null) {
            throw new IllegalStateException("cannot close question " + questionId + " before it is released");
        }
        Instant target = now.plus(CLOSE_NOTICE);
        // The extra second: deadlines are whole seconds from release, so a repeat press must not count as "later".
        if (!deadlineOf(question).isAfter(target.plusSeconds(1))) {
            throw new IllegalStateException("question " + question.getOrderIndex() + " is already closing or closed");
        }
        long secondsFromRelease = Duration.between(question.getReleasedAt(), target).toSeconds() + 1;
        question.setExtensionSeconds((int) secondsFromRelease - question.getDurationSeconds());
        questionRepository.save(question);
        return deadlineOf(question);
    }

    /** Deadline for an already-loaded question; fails if it was never released. */
    private Instant deadlineOf(Question question) {
        Instant releasedAt = question.getReleasedAt();
        if (releasedAt == null) {
            throw new IllegalStateException("question " + question.getId() + " has not been released yet");
        }
        return releasedAt.plusSeconds((long) question.getDurationSeconds() + question.getExtensionSeconds());
    }

    /** Loads the question or fails with "not found". */
    private Question requireQuestion(Long questionId) {
        return questionRepository
                .findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("question not found: " + questionId));
    }
}
