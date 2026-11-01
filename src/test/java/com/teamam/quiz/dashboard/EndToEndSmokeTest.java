package com.teamam.quiz.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamam.quiz.questionbank.RandomisationService;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.resilience.AcceptOutcome;
import com.teamam.quiz.resilience.AnswerSubmission;
import com.teamam.quiz.resilience.ResilienceService;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole lecture, through the same endpoints the screens call, with no manual steps. Deliberately
 * NOT @Transactional: a test-wide transaction hid a lazy-loading crash in Release in Week 4.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndToEndSmokeTest {

    private static final int STUDENTS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ResilienceService resilience;

    @Autowired
    private RandomisationService randomisation;

    @Autowired
    private QuestionRepository questions;

    // Guards against: any step of a real lecture only working when run by hand or in isolation.
    @Test
    void endToEndSmokeRunsThreeQuestionQuiz() throws Exception {
        long sid = read(mockMvc.perform(post("/api/sessions").contentType("application/json").content("{\"title\":\"smoke\"}"))
                .andExpect(status().isOk())).get("id").asLong();

        StringBuilder rosterCsv = new StringBuilder("rollno,name\n");
        List<String> rolls = new ArrayList<>();
        for (int i = 1; i <= STUDENTS; i++) {
            String roll = String.format("SMK%02d", i);
            rolls.add(roll);
            rosterCsv.append(roll).append(",Student ").append(i).append('\n');
        }
        mockMvc.perform(multipart("/api/roster/" + sid + "/upload")
                        .file(new MockMultipartFile("file", "r.csv", "text/csv", rosterCsv.toString().getBytes())))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/sessions/" + sid + "/questions/upload")
                        .file(new MockMultipartFile("file", "q.csv", "text/csv", """
                                MCQ,60,1,First,1,right,wrong
                                MCQ,60,2,Second,2,wrong,right
                                TYPED,60,5,Explain
                                """.getBytes())))
                .andExpect(jsonPath("$.added").value(3));

        mockMvc.perform(post("/api/sessions/" + sid + "/join-window/open").param("seconds", "60")).andExpect(status().isOk());
        String code = read(mockMvc.perform(get("/api/sessions/" + sid + "/join-window"))).get("code").asText();
        String ticket = read(mockMvc.perform(post("/api/sessions/" + sid + "/join-ticket").param("code", code))).get("ticket").asText();
        for (String roll : rolls) {
            mockMvc.perform(post("/api/roster/" + sid + "/join").contentType("application/json").content(
                            "{\"rollNo\":\"" + roll + "\",\"deviceToken\":\"d-" + roll + "\",\"ticket\":\"" + ticket + "\"}"))
                    .andExpect(status().isOk());
        }

        JsonNode dashboard = read(mockMvc.perform(get("/api/sessions/" + sid + "/dashboard")));
        assertThat(dashboard.get("joined").asInt()).isEqualTo(STUDENTS);
        for (JsonNode q : dashboard.get("questions")) {
            long qid = q.get("id").asLong();
            mockMvc.perform(post("/api/sessions/" + sid + "/questions/" + qid + "/release")).andExpect(status().isOk());
            for (String roll : rolls) {
                JsonNode state = read(mockMvc.perform(get("/api/sessions/" + sid + "/state")
                        .param("rollNo", roll).param("deviceToken", "d-" + roll)));
                assertThat(state.at("/currentQuestion/id").asLong()).isEqualTo(qid);
                AnswerSubmission answer = "TYPED".equals(q.get("type").asText())
                        ? new AnswerSubmission(roll + qid, roll, "d-" + roll, qid, null, "the same answer")
                        : new AnswerSubmission(roll + qid, roll, "d-" + roll, qid, indexOfRight(roll, qid), null);
                assertThat(resilience.accept(sid, answer).outcome()).isEqualTo(AcceptOutcome.ACCEPTED);
            }
        }

        JsonNode typed = dashboard.get("questions").get(2);
        JsonNode review = read(mockMvc.perform(get("/api/sessions/" + sid + "/questions/" + typed.get("id").asLong() + "/review")));
        assertThat(review.get("pending").asInt()).isEqualTo(STUDENTS);
        mockMvc.perform(post("/api/sessions/" + sid + "/questions/" + typed.get("id").asLong() + "/review/group")
                        .contentType("application/json")
                        .content("{\"key\":" + json.writeValueAsString(review.at("/groups/0/key").asText()) + ",\"marks\":4}"))
                .andExpect(jsonPath("$.updated").value(STUDENTS));

        byte[] xlsx = mockMvc.perform(get("/api/sessions/" + sid + "/export.xlsx"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet scores = workbook.getSheet("Scores");
            assertThat(scores.getLastRowNum()).isEqualTo(STUDENTS);
            for (int r = 1; r <= STUDENTS; r++) {
                assertThat(scores.getRow(r).getCell(5).getNumericCellValue()).isEqualTo(1 + 2 + 4); // Total
                assertThat(scores.getRow(r).getCell(6).getStringCellValue()).isEqualTo("PRESENT");
            }
        }
    }

    /** Where the option called "right" sits in this student's own shuffled view. */
    private int indexOfRight(String roll, long questionId) {
        var question = questions.findById(questionId).orElseThrow();
        return randomisation.shuffleFor(roll, question).indexOf("right");
    }

    private JsonNode read(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
