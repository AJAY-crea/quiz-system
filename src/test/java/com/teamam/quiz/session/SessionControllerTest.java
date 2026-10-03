package com.teamam.quiz.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.questionbank.QuestionBankService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Deliberately NOT @Transactional: a test transaction would keep the database session open while
 * the response is written, hiding lazy-loading failures that the real server hits.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionEngine engine;

    @Autowired
    private QuestionBankService bank;

    // Guards against: the instructor seeing "Internal Server Error" on every release because the
    // question's options were read after the database session had closed (found in the browser run).
    @Test
    void releaseEndpointReturnsTheQuestionWithItsOptions() throws Exception {
        QuizSession session = engine.createSession("release over HTTP");
        Question question = bank.add(session.getId(), 1, QuestionType.MCQ, "Pick C", TestQuiz.OPTIONS, 2, 60);

        mockMvc.perform(post("/api/sessions/" + session.getId() + "/questions/" + question.getId() + "/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(4));
    }
}
