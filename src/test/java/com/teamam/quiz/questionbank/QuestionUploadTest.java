package com.teamam.quiz.questionbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.session.SessionEngine;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QuestionUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionEngine engine;

    @Autowired
    private QuestionBankService bank;

    @Autowired
    private QuestionRepository questions;

    private static List<QuestionFileParser.ParsedQuestion> parse(String csv) throws Exception {
        return QuestionFileParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    // Guards against: commas in a question breaking its columns, or an answer key off by one.
    @Test
    void parsesHeaderQuotesAndBothTypes() throws Exception {
        List<QuestionFileParser.ParsedQuestion> parsed = parse("""
                type,seconds,marks,question,correct,option1,option2
                MCQ,60,2,"Which, of these?",2,"Red, dark",Blue,Green

                TYPED,120,5,"He said ""why""?"
                """);

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).text()).isEqualTo("Which, of these?");
        assertThat(parsed.get(0).options()).containsExactly("Red, dark", "Blue", "Green");
        assertThat(parsed.get(0).correctOptionIndex()).isEqualTo(1);
        assertThat(parsed.get(0).maxMarks()).isEqualTo(2);
        assertThat(parsed.get(1).type()).isEqualTo(QuestionType.TYPED);
        assertThat(parsed.get(1).text()).isEqualTo("He said \"why\"?");
        assertThat(parsed.get(1).correctOptionIndex()).isNull();
    }

    // Guards against: a vague failure when she has 30 questions in the file.
    @Test
    void errorsNameTheBadLine() {
        assertThatThrownBy(() -> parse("MCQ,60,1,fine,1,a,b\nESSAY,60,1,oops")).hasMessageContaining("Line 2");
        assertThatThrownBy(() -> parse("TYPED,soon,1,oops")).hasMessageContaining("seconds");
        assertThatThrownBy(() -> parse("\n\n")).hasMessageContaining("no questions");
    }

    // Guards against: half a file being added when one question in it is invalid (answer key 9 of 2).
    @Test
    void uploadAppendsAfterExistingQuestionsAndIsAllOrNothing() throws Exception {
        QuizSession session = engine.createSession("upload");
        Long sid = session.getId();
        bank.add(sid, 1, QuestionType.TYPED, "already here", null, null, 60);
        String url = "/api/sessions/" + sid + "/questions/upload";

        mockMvc.perform(multipart(url).file(file("MCQ,60,1,Q,1,a,b\nTYPED,60,5,T\n")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2));
        List<Question> stored = questions.findBySessionIdOrderByOrderIndex(sid);
        assertThat(stored).extracting(Question::getOrderIndex).containsExactly(1, 2, 3);
        assertThat(stored.get(2).getMaxMarks()).isEqualTo(5);

        mockMvc.perform(multipart(url).file(file("not a question line")))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> bank.addAll(sid, parse("MCQ,60,1,Bad,9,a,b")))
                .hasMessageContaining("Bad");
    }

    // Guards against: removal leaving a gap in the numbering, which would stop later releases.
    @Test
    void removingAQuestionClosesTheGapAndReleasedOnesCannotGo() {
        QuizSession session = engine.createSession("remove");
        Long sid = session.getId();
        Question first = bank.add(sid, 1, QuestionType.TYPED, "one", null, null, 60);
        Question second = bank.add(sid, 2, QuestionType.TYPED, "two", null, null, 60);
        Question third = bank.add(sid, 3, QuestionType.TYPED, "three", null, null, 60);
        engine.release(sid, first.getId());

        bank.remove(sid, second.getId());

        assertThat(questions.findBySessionIdOrderByOrderIndex(sid))
                .extracting(Question::getText).containsExactly("one", "three");
        engine.release(sid, third.getId()); // would refuse if "three" were still number 3
        assertThatThrownBy(() -> bank.remove(sid, first.getId())).isInstanceOf(IllegalStateException.class);
    }

    private static MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "q.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
