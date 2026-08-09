package com.tengames.auth.email;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** resend.com adapter — active as soon as RESEND_API_KEY is set. */
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final RestClient restClient;
    private final String from;

    public ResendEmailSender(RestClient.Builder restClientBuilder, MailProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + properties.resendApiKey())
                .build();
        this.from = properties.from();
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            restClient.post()
                    .uri("/emails")
                    .body(Map.of("from", from, "to", to, "subject", subject, "text", body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Resend explains itself in the body ("verify a domain at
            // resend.com/domains", "invalid api key") and nowhere else.
            throw failed(to, e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (RuntimeException e) {
            throw failed(to, String.valueOf(e.getMessage()), e);
        }
    }

    private static MailDeliveryException failed(String to, String detail, RuntimeException cause) {
        String message = "Sending mail to %s failed: %s".formatted(to, detail);
        log.error(message);
        return new MailDeliveryException(message, cause);
    }
}
