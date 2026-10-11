package com.teamam.quiz.questionbank;

import com.teamam.quiz.domain.Question;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.springframework.stereotype.Service;

/**
 * Per-student, per-question option shuffling. The permutation is derived deterministically
 * from (rollNo, questionId), so the same student sees the same shuffled order across a page
 * reload or reconnect, and remap() always inverts exactly the shuffle shuffleFor() produced.
 */
@Service
public class RandomisationService {

    /** This student's option order for this question (always the same for them). */
    public List<String> shuffleFor(String rollNo, Question question) {
        List<Integer> permutation = permutationFor(rollNo, question);
        List<String> options = question.getOptions();
        List<String> shuffled = new ArrayList<>(permutation.size());
        for (int canonicalIndex : permutation) {
            shuffled.add(options.get(canonicalIndex));
        }
        return shuffled;
    }

    /** Maps the index the student picked in their shuffled view back to the canonical option index. */
    public int remap(String rollNo, Question question, int chosenShuffledIndex) {
        return permutationFor(rollNo, question).get(chosenShuffledIndex);
    }

    /** The shuffle itself: positions 0..n-1 mixed by a generator seeded with (roll, question). */
    private List<Integer> permutationFor(String rollNo, Question question) {
        int optionCount = question.getOptions().size();
        List<Integer> indices = new ArrayList<>(optionCount);
        for (int i = 0; i < optionCount; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, new Random(seedFor(rollNo, question.getId())));
        return indices;
    }

    /** Seed from (roll number, question id): same inputs give the same shuffle every time. */
    private long seedFor(String rollNo, Long questionId) {
        return Objects.hash(rollNo, questionId);
    }
}
