package com.teamam.quiz.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** With --quiz.public-url (a tunnel link), phones on mobile data can be given a QR they can open. */
@SpringBootTest(properties = "quiz.public-url=https://demo-link.trycloudflare.com/")
@AutoConfigureMockMvc
class PublicLinkTest {

    @Autowired
    private MockMvc mockMvc;

    // Guards against: the internet link not being offered, or not being the default QR address.
    @Test
    void publicLinkIsOfferedFirst() throws Exception {
        mockMvc.perform(get("/api/dashboard/host-addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value("https://demo-link.trycloudflare.com"))
                .andExpect(jsonPath("$[0].adapter").value("Internet link: works on mobile data"));
    }

    // Guards against: needing an app restart to use a new tunnel link, or a link that would put
    // something other than an https address into every student's QR.
    @Test
    void linkCanBePastedOnTheDashboardButOnlyAPlainHttpsOne() throws Exception {
        mockMvc.perform(post("/api/dashboard/public-url").param("url", "https://new-words.trycloudflare.com/"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/host-addresses"))
                .andExpect(jsonPath("$[0].url").value("https://new-words.trycloudflare.com"));

        mockMvc.perform(post("/api/dashboard/public-url").param("url", "javascript:alert(1)"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/dashboard/public-url").param("url", "http://evil.example.com"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/dashboard/public-url").param("url", "https://x.trycloudflare.com")
                        .with(request -> { request.setRemoteAddr("192.168.1.50"); return request; }))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/dashboard/public-url").param("url", "https://demo-link.trycloudflare.com"));
    }
}
