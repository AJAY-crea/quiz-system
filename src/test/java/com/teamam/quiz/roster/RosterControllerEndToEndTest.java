package com.teamam.quiz.roster;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.repository.QuizSessionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The whole joining path over HTTP, as the instructor's dashboard and a student's phone drive it:
 * upload the CSV roster, open joining, read the rotating code, exchange it for a ticket, join.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RosterControllerEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuizSessionRepository sessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // Guards against: any step of the real scan-and-join flow being broken end to end.
    @Test
    void uploadsTestRosterAndVerifiesJoinsOverHttp() throws Exception {
        QuizSession session = sessionRepository.save(new QuizSession("End-to-end roster test"));
        String base = "/api/sessions/" + session.getId();

        Path fixture = new ClassPathResource("fixtures/test-roster.csv").getFile().toPath();
        MockMultipartFile rosterFile =
                new MockMultipartFile("file", "test-roster.csv", "text/csv", Files.readAllBytes(fixture));
        mockMvc.perform(multipart("/api/roster/" + session.getId() + "/upload").file(rosterFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rosterSize").value(30));

        mockMvc.perform(post(base + "/join-ticket").param("code", "NOPE"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(base + "/join-window/open").param("seconds", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true));
        String code = json(mockMvc.perform(get(base + "/join-window")).andReturn().getResponse().getContentAsString())
                .get("code").asText();
        String ticket = json(mockMvc.perform(post(base + "/join-ticket").param("code", code))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("ticket").asText();

        mockMvc.perform(post("/api/roster/" + session.getId() + "/join")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new JoinRequest("CS23B001", "device-A", ticket))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"));

        mockMvc.perform(post("/api/roster/" + session.getId() + "/join")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new JoinRequest("XX99Z999", "device-B", ticket))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.outcome").value("REJECTED_UNLISTED"));

        mockMvc.perform(post("/api/roster/" + session.getId() + "/join")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new JoinRequest("CS23B002", "device-C", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.outcome").value("REJECTED_INVALID_TICKET"));
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
