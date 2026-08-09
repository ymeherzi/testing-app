package com.tengames.common.web;

import com.tengames.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SpaForwardingIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void spaRoutesForwardToTheShell() throws Exception {
        for (String route : new String[]{"/", "/login", "/signup", "/table", "/profile", "/admin"}) {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void apiRoutesAreNeverForwarded() throws Exception {
        // unauthenticated API access stays a 401, not the SPA shell
        mockMvc.perform(get("/api/gameweeks/current")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointStaysPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
