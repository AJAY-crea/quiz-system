package com.teamam.quiz.roster;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Exercises the actual HTTP path an instructor's roster upload and a student's join take:
 * a real CSV fixture uploaded via multipart, then a join request over REST, matching the
 * design doc's "roster upload + verify working end to end against a test roster" deliverable.
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

    @Test
    void uploadsTestRosterAndVerifiesJoinsOverHttp() throws Exception {
        QuizSession session = sessionRepository.save(new QuizSession("End-to-end roster test"));

        Path fixture = new ClassPathResource("fixtures/test-roster.csv").getFile().toPath();
        MockMultipartFile rosterFile =
                new MockMultipartFile("file", "test-roster.csv", "text/csv", Files.readAllBytes(fixture));

        mockMvc.perform(multipart("/api/roster/" + session.getId() + "/upload").file(rosterFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rosterSize").value(30));

        JoinRequest listedJoin = new JoinRequest("CS23B001", "device-A");
        mockMvc.perform(post("/api/roster/" + session.getId() + "/join")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(listedJoin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"));

        JoinRequest unlistedJoin = new JoinRequest("XX99Z999", "device-B");
        mockMvc.perform(post("/api/roster/" + session.getId() + "/join")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(unlistedJoin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.outcome").value("REJECTED_UNLISTED"));
    }
}
