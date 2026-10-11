package com.teamam.quiz.scoring;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.questionbank.RandomisationService;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * MCQ auto-scoring. The student's chosen index is in their own shuffled view, so it is
 * remapped back to the canonical option index (the same permutation Randomisation handed
 * them) before comparing against the answer key. Typed-answer review is a later module.
 */
@Service
public class ScoringService {

    private final RandomisationService randomisationService;

    public ScoringService(RandomisationService randomisationService) {
        this.randomisationService = randomisationService;
    }

    /** Correct if the student's tap, un-shuffled back to the real option, equals the answer key. */
    public boolean scoreMcq(String rollNo, Question question, int chosenShuffledIndex) {
        if (question.getType() != QuestionType.MCQ) {
            throw new IllegalArgumentException("scoreMcq called on a non-MCQ question: " + question.getId());
        }
        int canonicalIndex = randomisationService.remap(rollNo, question, chosenShuffledIndex);
        return Objects.equals(canonicalIndex, question.getCorrectOptionIndex());
    }
}
