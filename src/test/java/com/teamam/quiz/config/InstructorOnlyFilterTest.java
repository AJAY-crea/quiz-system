package com.teamam.quiz.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class InstructorOnlyFilterTest {

    private static final RequestPostProcessor FROM_A_STUDENT_PHONE = request -> {
        request.setRemoteAddr("192.168.1.50");
        return request;
    };

    @Autowired
    private MockMvc mockMvc;

    // Guards against: any student on the classroom Wi-Fi releasing questions, reading the live join
    // code, uploading a roster, or reading attendance.
    @Test
    void instructorEndpointsRefuseLanAddresses() throws Exception {
        mockMvc.perform(post("/api/sessions").with(FROM_A_STUDENT_PHONE)
                        .contentType("application/json").content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(InstructorOnlyFilter.MESSAGE));
        mockMvc.perform(post("/api/sessions/1/questions/1/release").with(FROM_A_STUDENT_PHONE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/sessions/1/join-window").with(FROM_A_STUDENT_PHONE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/sessions/1/attendance").with(FROM_A_STUDENT_PHONE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/instructor.html").with(FROM_A_STUDENT_PHONE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/sessions/1/export.xlsx").with(FROM_A_STUDENT_PHONE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/sessions/1/doubts").with(FROM_A_STUDENT_PHONE))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/sessions/1/doubts/1/dismiss").with(FROM_A_STUDENT_PHONE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/sessions/1/integrity").with(FROM_A_STUDENT_PHONE))
                .andExpect(status().isForbidden());
    }

    // Guards against: phones being unable to send a doubt or an integrity signal.
    @Test
    void doubtAndIntegrityPostsStayReachableFromLan() throws Exception {
        mockMvc.perform(post("/api/sessions/999/doubts").with(FROM_A_STUDENT_PHONE)
                        .contentType("application/json").content("{\"rollNo\":\"A\",\"deviceToken\":\"d\",\"text\":\"q\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/sessions/999/integrity").with(FROM_A_STUDENT_PHONE)
                        .contentType("application/json")
                        .content("{\"rollNo\":\"A\",\"deviceToken\":\"d\",\"questionId\":1,\"eventType\":\"PASTE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"));
    }

    // Guards against: the filter also blocking what phones legitimately need (these reach the
    // controllers and fail only because session 999 doesn't exist).
    @Test
    void studentEndpointsStayReachableFromLan() throws Exception {
        mockMvc.perform(post("/api/sessions/999/join-ticket").param("code", "x").with(FROM_A_STUDENT_PHONE))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/roster/999/join").with(FROM_A_STUDENT_PHONE)
                        .contentType("application/json").content("{\"rollNo\":\"A\",\"deviceToken\":\"d\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/sessions/999/state").param("rollNo", "A").param("deviceToken", "d")
                        .with(FROM_A_STUDENT_PHONE))
                .andExpect(status().isNotFound());
    }

    // Guards against: locking the instructor out of her own laptop.
    @Test
    void instructorEndpointsWorkFromLocalhost() throws Exception {
        mockMvc.perform(post("/api/sessions").contentType("application/json").content("{\"title\":\"x\"}"))
                .andExpect(status().isOk());
    }
}
