package com.predictor.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.predictor.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String signup(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "correct-horse", "displayName": "Alex",
                                 "country": "FR"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }

    @Test
    void signupThenFetchProfile() throws Exception {
        String token = signup("alex@example.com");

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alex"))
                .andExpect(jsonPath("$.country").value("FR"))
                .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        signup("dup@example.com");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "DUP@example.com", "password": "correct-horse", "displayName": "Copy"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        signup("login@example.com");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "login@example.com", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());

        MvcResult ok = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "login@example.com", "password": "correct-horse"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(ok.getResponse().getContentAsString()).get("token").asText()).isNotBlank();
    }

    @Test
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsAreForbiddenForRegularUsers() throws Exception {
        String token = signup("regular@example.com");

        mockMvc.perform(get("/api/admin/matches").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileCanBeUpdated() throws Exception {
        String token = signup("editor@example.com");

        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Alexandra", "country": "TN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alexandra"))
                .andExpect(jsonPath("$.country").value("TN"));
    }

    @Test
    void invalidSignupIsRejectedWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "short", "displayName": "A"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").isNotEmpty())
                .andExpect(jsonPath("$.errors.password").isNotEmpty())
                .andExpect(jsonPath("$.errors.displayName").isNotEmpty());
    }
}
