package com.predictor.auth.email;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * A refused message must not look like a delivered one — the whole point of
 * this adapter throwing rather than logging.
 */
class ResendEmailSenderTest {

    private static final String DOMAIN_ERROR = """
            {"statusCode":403,"name":"validation_error","message":"You can only send testing emails \
            to your own email address. To send emails to other recipients, please verify a domain \
            at resend.com/domains, and change the `from` address to an email using this domain."}""";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final EmailSender sender = new ResendEmailSender(
            builder, new MailProperties("re_test_key", "Warga <no-reply@warga.app>", null));

    @Test
    void anAcceptedMessagePostsTheCodeToResend() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(jsonPath("$.from").value("Warga <no-reply@warga.app>"))
                .andExpect(jsonPath("$.to").value("player@example.com"))
                .andExpect(jsonPath("$.text").value("Your code is 123456"))
                .andRespond(withSuccess("{\"id\":\"abc\"}", MediaType.APPLICATION_JSON));

        assertThatCode(() -> sender.send("player@example.com", "Your code", "Your code is 123456"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void anUnverifiedSendingDomainIsReportedWithTheProvidersOwnWords() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(DOMAIN_ERROR));

        assertThatThrownBy(() -> sender.send("friend@example.com", "Your code", "Your code is 123456"))
                .isInstanceOf(MailDeliveryException.class)
                .hasMessageContaining("friend@example.com")
                .hasMessageContaining("403")
                .hasMessageContaining("verify a domain");
    }

    @Test
    void aProviderOutageIsAlsoAFailedDelivery() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> sender.send("player@example.com", "Your code", "Your code is 123456"))
                .isInstanceOf(MailDeliveryException.class);
    }
}
