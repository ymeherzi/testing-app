package com.predictor.auth;

import com.predictor.TestcontainersConfiguration;
import com.predictor.auth.email.EmailSender;
import com.predictor.user.User;
import com.predictor.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthFlowIT.CapturingMailConfig.class})
@ActiveProfiles("test")
class AuthFlowIT {

    /** Captures the codes that would have been emailed. */
    static class CapturingMailSender implements EmailSender {
        final List<String> bodies = new ArrayList<>();

        @Override
        public void send(String to, String subject, String body) {
            bodies.add(body);
        }

        String latestCode() {
            Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(bodies.getLast());
            assertThat(matcher.find()).isTrue();
            return matcher.group(1);
        }
    }

    @TestConfiguration
    static class CapturingMailConfig {
        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private CapturingMailSender mail;

    private JsonNode call(String path, String body, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }

    private String signupAndVerify(String email, boolean rememberDevice) throws Exception {
        call("/api/auth/signup", """
                {"email": "%s", "password": "correct-horse", "displayName": "Alex", "country": "FR"}
                """.formatted(email), 200);
        JsonNode verified = call("/api/auth/verify", """
                {"email": "%s", "code": "%s", "rememberDevice": %s}
                """.formatted(email, mail.latestCode(), rememberDevice), 200);
        return verified.get("token").asText();
    }

    @Test
    void signupSendsCodeAndOnlyTheCodeUnlocksTheAccount() throws Exception {
        JsonNode signup = call("/api/auth/signup", """
                {"email": "alex@example.com", "password": "correct-horse", "displayName": "Alex", "country": "FR"}
                """, 200);
        assertThat(signup.get("verificationRequired").asBoolean()).isTrue();
        assertThat(signup.get("token").isNull()).isTrue();
        assertThat(users.findByEmailIgnoreCase("alex@example.com").orElseThrow().isEmailVerified()).isFalse();

        // a wrong code is refused
        call("/api/auth/verify", """
                {"email": "alex@example.com", "code": "000000", "rememberDevice": false}
                """, 400);

        JsonNode verified = call("/api/auth/verify", """
                {"email": "alex@example.com", "code": "%s", "rememberDevice": false}
                """.formatted(mail.latestCode()), 200);
        assertThat(verified.get("token").asText()).isNotBlank();
        assertThat(users.findByEmailIgnoreCase("alex@example.com").orElseThrow().isEmailVerified()).isTrue();

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + verified.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alex"))
                .andExpect(jsonPath("$.country").value("FR"));
    }

    @Test
    void unknownDeviceNeedsACodeButARememberedOneDoesNot() throws Exception {
        signupAndVerify("device@example.com", false);

        // logging in from an unrecognized device only sends a code
        JsonNode challenge = call("/api/auth/login", """
                {"email": "device@example.com", "password": "correct-horse"}
                """, 200);
        assertThat(challenge.get("verificationRequired").asBoolean()).isTrue();
        assertThat(challenge.get("token").isNull()).isTrue();

        // confirming it and asking to be remembered returns a device token
        JsonNode verified = call("/api/auth/verify", """
                {"email": "device@example.com", "code": "%s", "rememberDevice": true}
                """.formatted(mail.latestCode()), 200);
        String deviceToken = verified.get("deviceToken").asText();
        assertThat(deviceToken).isNotBlank();

        // that device now signs straight in
        JsonNode direct = call("/api/auth/login", """
                {"email": "device@example.com", "password": "correct-horse", "deviceToken": "%s"}
                """.formatted(deviceToken), 200);
        assertThat(direct.get("verificationRequired").asBoolean()).isFalse();
        assertThat(direct.get("token").asText()).isNotBlank();

        // someone else's stolen-looking token does not
        JsonNode otherDevice = call("/api/auth/login", """
                {"email": "device@example.com", "password": "correct-horse", "deviceToken": "deadbeef"}
                """, 200);
        assertThat(otherDevice.get("verificationRequired").asBoolean()).isTrue();
    }

    @Test
    void wrongPasswordIsRejectedBeforeAnyCodeIsSent() throws Exception {
        signupAndVerify("login@example.com", false);
        int sentBefore = mail.bodies.size();

        call("/api/auth/login", """
                {"email": "login@example.com", "password": "wrong-password"}
                """, 401);
        assertThat(mail.bodies).hasSize(sentBefore);
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        signupAndVerify("dup@example.com", false);
        call("/api/auth/signup", """
                {"email": "DUP@example.com", "password": "correct-horse", "displayName": "Copy"}
                """, 409);
    }

    @Test
    void profileRequiresAuthenticationAndAdminRoutesRequireTheRole() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        String token = signupAndVerify("regular@example.com", false);
        mockMvc.perform(get("/api/admin/matches").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileCanBeUpdated() throws Exception {
        String token = signupAndVerify("editor@example.com", false);
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

    @Test
    void googleSignInIsAdvertisedAsOffWhenUnconfigured() throws Exception {
        mockMvc.perform(get("/api/auth/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(false));
    }
}
