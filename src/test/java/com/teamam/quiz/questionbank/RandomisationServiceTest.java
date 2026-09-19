package com.teamam.quiz.questionbank;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class RandomisationServiceTest {

    private final RandomisationService randomisationService = new RandomisationService();

    @Test
    void shuffledOptionMapsToCorrectKey() throws Exception {
        Question question = questionWithId(101L, List.of("A", "B", "C", "D"));
        int correctOptionIndex = 2; // "C"

        for (int i = 1; i <= 20; i++) {
            String rollNo = String.format("CS23B%03d", i);

            List<String> shuffled = randomisationService.shuffleFor(rollNo, question);
            assertThat(shuffled).containsExactlyInAnyOrder("A", "B", "C", "D");

            int shuffledPositionOfCorrectAnswer = shuffled.indexOf("C");
            int remapped = randomisationService.remap(rollNo, question, shuffledPositionOfCorrectAnswer);

            assertThat(remapped).isEqualTo(correctOptionIndex);
        }
    }

    /** Question's id is DB-generated; a plain unit test sets it via reflection to avoid booting Spring. */
    private static Question questionWithId(long id, List<String> options) throws Exception {
        Question question = new Question(null, 1, QuestionType.MCQ, "Which letter is C?");
        question.setOptions(options);
        Field idField = Question.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(question, id);
        return question;
    }
}
