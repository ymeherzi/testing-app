package com.prono10.auth.email;

import com.prono10.auth.email.MailProperties.Smtp;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which sender is wired, and the one misconfiguration that would otherwise
 * only surface as a rejection once a real person tried to sign up.
 */
class MailConfigTest {

    private static final Smtp NO_SMTP = new Smtp(null, null, null, null);
    private static final Smtp BREVO = new Smtp("smtp-relay.brevo.com", 587, "user", "key");

    private final MailConfig config = new MailConfig();

    private EmailSender senderFor(MailProperties properties) {
        return config.emailSender(properties, RestClient.builder());
    }

    @Test
    void withoutAProviderMailIsOnlyLogged() {
        assertThat(senderFor(new MailProperties(NO_SMTP, null, null, null)))
                .isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    void blankSettingsDoNotCountAsConfigured() {
        assertThat(senderFor(new MailProperties(new Smtp("  ", null, null, null), "  ", null, null)))
                .isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    void anSmtpRelayIsUsedWhenOneIsConfigured() {
        assertThat(senderFor(new MailProperties(BREVO, null, "Warga <you@example.com>", null)))
                .isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void smtpWinsOverResendBecauseItReachesAnyRecipient() {
        assertThat(senderFor(new MailProperties(BREVO, "re_test_key", "Warga <you@example.com>", null)))
                .isInstanceOf(SmtpEmailSender.class);
    }

    @Test
    void resendIsUsedWhenItIsTheOnlyProvider() {
        assertThat(senderFor(new MailProperties(NO_SMTP, "re_test_key", "Warga <you@example.com>", null)))
                .isInstanceOf(ResendEmailSender.class);
    }

    @Test
    void aProviderWithoutASenderAddressFailsAtStartup() {
        assertThatThrownBy(() -> new MailProperties(NO_SMTP, "re_test_key", "", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
        assertThatThrownBy(() -> new MailProperties(BREVO, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
    }

    @Test
    void submissionPortIsAssumedWhenNoneIsGiven() {
        assertThat(new Smtp("smtp-relay.brevo.com", null, null, null).port()).isEqualTo(587);
    }
}
