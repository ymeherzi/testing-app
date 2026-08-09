package com.tengames.auth;

import com.tengames.TestcontainersConfiguration;
import com.tengames.auth.email.EmailSender;
import com.tengames.auth.verification.AuthCode;
import com.tengames.auth.verification.VerificationService;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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
 * The rules that make a login code safe: it dies with time, it dies after
 * guessing, and it works exactly once. These are invisible in normal use,
 * so nothing but a test protects them from a future refactor.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, VerificationRulesIT.Fixtures.class})
@ActiveProfiles("test")
class VerificationRulesIT {

    /** Lets a test move time forward without sleeping. */
    static class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-08-01T12:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

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
    static class Fixtures {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }

        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }

    @Autowired
    private VerificationService verification;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MovableClock clock;

    @Autowired
    private CapturingMailSender mail;

    private User user;

    /**
     * A fresh address per test: codes are rate-limited per address, so tests
     * sharing one would start failing each other as soon as the suite grew.
     */
    private User freshUser() {
        String email = "rules-%s@example.com".formatted(java.util.UUID.randomUUID());
        return users.save(new User(email, passwordEncoder.encode("correct-horse"), "Rules", "TN", null));
    }

    @BeforeEach
    void setUp() {
        user = freshUser();
    }

    @Test
    void aCodeStopsWorkingAfterFifteenMinutes() {
        verification.sendCode(user, AuthCode.Purpose.VERIFY_EMAIL);
        String code = mail.latestCode();

        clock.advance(Duration.ofMinutes(14));
        // still inside the window: this must not throw
        verification.consumeCode(user, AuthCode.Purpose.VERIFY_EMAIL, code);

        verification.sendCode(user, AuthCode.Purpose.VERIFY_EMAIL);
        String second = mail.latestCode();
        clock.advance(Duration.ofMinutes(16));
        assertThatThrownBy(() -> verification.consumeCode(user, AuthCode.Purpose.VERIFY_EMAIL, second))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void guessingIsCappedAtFiveAttempts() {
        verification.sendCode(user, AuthCode.Purpose.NEW_DEVICE);
        String code = mail.latestCode();

        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> verification.consumeCode(user, AuthCode.Purpose.NEW_DEVICE, "000000"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not right");
        }
        // the code is burned even though the sixth guess is the correct one
        assertThatThrownBy(() -> verification.consumeCode(user, AuthCode.Purpose.NEW_DEVICE, code))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void aCodeCannotBeUsedTwice() {
        verification.sendCode(user, AuthCode.Purpose.VERIFY_EMAIL);
        String code = mail.latestCode();
        verification.consumeCode(user, AuthCode.Purpose.VERIFY_EMAIL, code);

        assertThatThrownBy(() -> verification.consumeCode(user, AuthCode.Purpose.VERIFY_EMAIL, code))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void issuingANewCodeRetiresThePreviousOne() {
        verification.sendCode(user, AuthCode.Purpose.VERIFY_EMAIL);
        String first = mail.latestCode();
        verification.sendCode(user, AuthCode.Purpose.VERIFY_EMAIL);
        String second = mail.latestCode();
        assertThat(first).isNotEqualTo(second);

        // only the newest code is accepted (this is why a resend invalidates the old one)
        assertThatThrownBy(() -> verification.consumeCode(user, AuthCode.Purpose.VERIFY_EMAIL, first))
                .isInstanceOf(ResponseStatusException.class);
        verification.consumeCode(user, AuthCode.Purpose.VERIFY_EMAIL, second);
    }

    @Test
    void aDeviceCodeCannotStandInForAnEmailVerification() {
        verification.sendCode(user, AuthCode.Purpose.NEW_DEVICE);
        String deviceCode = mail.latestCode();

        assertThatThrownBy(() -> verification.consumeCode(user, AuthCode.Purpose.VERIFY_EMAIL, deviceCode))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void anAddressCannotBeMailedEndlessly() {
        for (int sent = 1; sent <= 5; sent++) {
            verification.sendCode(user, AuthCode.Purpose.VERIFY_EMAIL);
        }
        int delivered = mail.bodies.size();

        assertThatThrownBy(() -> verification.sendCode(user, AuthCode.Purpose.VERIFY_EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Too many codes");
        // the refusal must happen before the provider is called, or the quota
        // is spent anyway and the address still receives the mail
        assertThat(mail.bodies).hasSize(delivered);

        clock.advance(Duration.ofHours(1).plusMinutes(1));
        verification.sendCode(user, AuthCode.Purpose.VERIFY_EMAIL);
        assertThat(mail.bodies).hasSize(delivered + 1);
    }

    @Test
    void rememberedDevicesExpireAndBelongToOneAccountOnly() {
        String token = verification.rememberDevice(user, "test-device");
        assertThat(verification.isTrustedDevice(user, token)).isTrue();
        assertThat(verification.isTrustedDevice(user, "not-a-real-token")).isFalse();
        assertThat(verification.isTrustedDevice(user, null)).isFalse();

        assertThat(verification.isTrustedDevice(freshUser(), token)).isFalse();

        clock.advance(Duration.ofDays(61));
        assertThat(verification.isTrustedDevice(user, token)).isFalse();
    }
}
