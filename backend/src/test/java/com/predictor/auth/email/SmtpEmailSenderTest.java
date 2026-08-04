package com.predictor.auth.email;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A relay that refuses the message must not look like one that delivered it —
 * the whole reason this adapter throws instead of logging.
 */
class SmtpEmailSenderTest {

    /** Records what was handed to the relay, or refuses like a real one would. */
    static class FakeRelay extends JavaMailSenderImpl {
        final List<SimpleMailMessage> sent = new ArrayList<>();
        RuntimeException failure;

        @Override
        public void send(SimpleMailMessage... messages) {
            if (failure != null) {
                throw failure;
            }
            sent.addAll(List.of(messages));
        }
    }

    private final FakeRelay relay = new FakeRelay();
    private final EmailSender sender = new SmtpEmailSender(relay, "Warga <you@example.com>");

    @Test
    void aDeliveredMessageCarriesTheCodeToTheRecipient() {
        sender.send("player@example.com", "Your code", "Your code is 123456");

        assertThat(relay.sent).singleElement().satisfies(message -> {
            assertThat(message.getFrom()).isEqualTo("Warga <you@example.com>");
            assertThat(message.getTo()).containsExactly("player@example.com");
            assertThat(message.getSubject()).isEqualTo("Your code");
            assertThat(message.getText()).isEqualTo("Your code is 123456");
        });
    }

    @Test
    void anUnverifiedSenderIsReportedWithTheRelaysOwnWords() {
        relay.failure = new MailSendException("550 sender you@example.com is not authorised");

        assertThatThrownBy(() -> sender.send("friend@example.com", "Your code", "Your code is 123456"))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessageContaining("friend@example.com")
                .hasMessageContaining("not authorised");
    }

    @Test
    void anUnreachableRelayIsAlsoAFailedDelivery() {
        relay.failure = new MailSendException("connection timed out");

        assertThatThrownBy(() -> sender.send("player@example.com", "Your code", "Your code is 123456"))
                .isInstanceOf(MailDeliveryException.class);
    }
}
