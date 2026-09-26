package com.teamam.quiz.dashboard;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.domain.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private QuestionBankService bank;

    // Guards against: the instructor screen showing wrong live counts or question states.
    @Test
    void dashboardShowsLiveCountsAndQuestionStates() throws Exception {
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, "CS23B701", "CS23B702");
        bank.add(quiz.sessionId, 2, QuestionType.MCQ, "Next one", TestQuiz.OPTIONS, 1, 60);

        mockMvc.perform(get("/api/sessions/" + quiz.sessionId + "/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rosterSize").value(2))
                .andExpect(jsonPath("$.joined").value(2))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0].released").value(true))
                .andExpect(jsonPath("$.questions[0].remainingMs").isNumber())
                .andExpect(jsonPath("$.questions[1].released").value(false))
                .andExpect(jsonPath("$.join.open").value(true));
    }

    // Guards against: a QR shown while joining is closed, or an attacker-controlled link inside the QR.
    @Test
    void joinQrOnlyWhileOpenAndOnlyForAPlainBaseUrl() throws Exception {
        TestQuiz quiz = TestQuiz.startWithReleasedMcq(ctx, 60, "CS23B703");
        String qr = "/api/sessions/" + quiz.sessionId + "/join-qr.svg";

        mockMvc.perform(get(qr).param("base", "http://192.168.1.5:8080"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(content().string(startsWith("<svg")));

        mockMvc.perform(get(qr).param("base", "javascript:alert(1)"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/sessions/" + quiz.sessionId + "/join-window/close"));
        mockMvc.perform(get(qr).param("base", "http://192.168.1.5:8080"))
                .andExpect(status().isConflict());
    }
}
