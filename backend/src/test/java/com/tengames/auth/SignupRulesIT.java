package com.tengames.auth;

import com.tengames.TestcontainersConfiguration;
import com.tengames.auth.email.MailDomainLookup;
import java.util.UUID;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two ways a signup is now refused, end to end: a password anybody could
 * guess, and an address that will never receive the code.
 *
 * <p>Both answer with a code as well as a sentence — the app is bilingual, and
 * the client says these in the player's own language.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SignupRulesIT.NoSuchDomainConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.auth.check-email-domain=true",
        "app.auth.check-breached-passwords=true"})
class SignupRulesIT {

    /** One domain does not exist and the rest do: no test may depend on real DNS. */
    @TestConfiguration
    static class NoSuchDomainConfig {
        @Bean
        @Primary
        MailDomainLookup missingOnlyTheTypo() {
            return domain -> domain.equals("asba.fr")
                    ? MailDomainLookup.Verdict.MISSING
                    : MailDomainLookup.Verdict.ACCEPTS;
        }

        /**
         * "chocolate1" is the real thing: it clears every offline rule we have
         * and has been seen 567,912 times in breaches. Stubbed rather than
         * fetched — no test may depend on somebody else's service.
         */
        @Bean
        @Primary
        BreachedPasswords onlyChocolateLeaked() {
            return password -> password.equals("chocolate1")
                    ? BreachedPasswords.Verdict.BREACHED
                    : BreachedPasswords.Verdict.CLEAN;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private void signup(String email, String password, int status, String code) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "displayName": "Alex"}
                                """.formatted(email, password)))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void aGuessablePasswordIsRefusedWithTheRuleItBroke() throws Exception {
        signup("weak-%s@example.com".formatted(UUID.randomUUID()), "12341234", 400, "password.tooShort");
    }

    @Test
    void aPasswordOfRepeatedCharactersIsRefused() throws Exception {
        signup("weak-%s@example.com".formatted(UUID.randomUUID()), "1234123412", 400, "password.repeated");
    }

    @Test
    void aPasswordFromAKnownBreachIsRefused() throws Exception {
        // it passes every rule PasswordPolicy has: ten characters, no
        // repetition, no keyboard run, not on our short list
        signup("leaked-%s@example.com".formatted(UUID.randomUUID()), "chocolate1", 400,
                "password.breached");
    }

    @Test
    void aPasswordNobodyHasLeakedGoesThrough() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "clean-%s@example.com", "password": "vivelefoot10",
                                 "displayName": "Alex"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    void anAddressThatCannotReceiveMailIsRefused() throws Exception {
        signup("bahla@asba.fr", "correct horse battery staple", 400, "email.unreachable");
    }

    @Test
    void theMessageAlsoLandsUnderTheFieldTheFormShows() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "bahla@asba.fr", "password": "correct horse battery staple",
                                 "displayName": "Alex"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").isNotEmpty());
    }
}
