package com.predictor.auth.email;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which sender is wired, and the one misconfiguration that would otherwise
 * only surface as a 403 from Resend once a real person tried to sign up.
 */
class MailConfigTest {

    private final MailConfig config = new MailConfig();

    @Test
    void withoutAnApiKeyMailIsOnlyLogged() {
        EmailSender sender = config.emailSender(
                new MailProperties(null, null, null), RestClient.builder());

        assertThat(sender).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    void anEmptyApiKeyDoesNotCountAsConfigured() {
        EmailSender sender = config.emailSender(
                new MailProperties("  ", null, null), RestClient.builder());

        assertThat(sender).isInstanceOf(LoggingEmailSender.class);
    }

    @Test
    void anApiKeyAndASenderAddressGiveRealMail() {
        EmailSender sender = config.emailSender(
                new MailProperties("re_test_key", "Warga <no-reply@warga.app>", null), RestClient.builder());

        assertThat(sender).isInstanceOf(ResendEmailSender.class);
    }

    @Test
    void anApiKeyWithoutASenderAddressFailsAtStartup() {
        assertThatThrownBy(() -> new MailProperties("re_test_key", "", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
    }
}
