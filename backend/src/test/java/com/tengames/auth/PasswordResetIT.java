package com.tengames.auth;

import com.tengames.TestcontainersConfiguration;
import com.tengames.auth.email.EmailSender;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Forgetting a password must be recoverable, and recovery must not become a
 * way in. The code is the whole proof, so what matters is that it is
 * required, single-purpose, and that the old password really dies.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, PasswordResetIT.Fixtures.class})
@ActiveProfiles("test")
class PasswordResetIT {

    static class CapturingMailSender implements EmailSender {
        final List<String> subjects = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();

        @Override
        public void send(String to, String subject, String body) {
            subjects.add(subject);
            bodies.add(body);
        }

        String latestCode() {
            Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(bodies.getLast());
            assertThat(matcher.find()).isTrue();
            return matcher.group(1);
        }
    }

    @TestConfiguration
    static class Fixtures {
        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }

    @Autowired
    private AuthService auth;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CapturingMailSender mail;

    private String email;

    @BeforeEach
    void setUp() {
        email = "reset-%s@example.com".formatted(UUID.randomUUID());
        users.save(new User(email, passwordEncoder.encode("forgotten-one"), "Reset", "TN", null));
    }

    @Test
    void aForgottenPasswordCanBeReplacedAndTheOldOneStopsWorking() {
        auth.forgotPassword(email);
        assertThat(mail.subjects.getLast()).contains("Reset");

        var response = auth.resetPassword(email, mail.latestCode(), "a-brand-new-one", "test-device");

        assertThat(response.token()).isNotBlank();
        User stored = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwordEncoder.matches("a-brand-new-one", stored.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("forgotten-one", stored.getPasswordHash())).isFalse();
        // reaching the inbox is the same proof signup asks for
        assertThat(stored.isEmailVerified()).isTrue();
    }

    @Test
    void aWrongCodeLeavesThePasswordAlone() {
        auth.forgotPassword(email);

        assertThatThrownBy(() -> auth.resetPassword(email, "000000", "attacker-choice", "test-device"))
                .isInstanceOf(ResponseStatusException.class);

        User stored = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwordEncoder.matches("forgotten-one", stored.getPasswordHash())).isTrue();
    }

    @Test
    void aSignInCodeCannotBeSpentOnAPasswordReset() {
        // the account asks for a login code, an attacker tries to redeem it as a reset
        auth.login(new AuthDtos.LoginRequest(email, "forgotten-one", null), "test-device");
        String signInCode = mail.latestCode();

        assertThatThrownBy(() -> auth.resetPassword(email, signInCode, "attacker-choice", "test-device"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void anUnknownAddressIsAnsweredLikeAKnownOne() {
        int sent = mail.bodies.size();

        var response = auth.forgotPassword("nobody-%s@example.com".formatted(UUID.randomUUID()));

        // same shape of answer, but nothing sent: the endpoint must not reveal who plays
        assertThat(response.verificationRequired()).isTrue();
        assertThat(mail.bodies).hasSize(sent);
    }
}
