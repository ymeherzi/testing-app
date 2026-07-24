package com.predictor.auth.email;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

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
        } catch (Exception e) {
            // Never break signup because mail is down; the user can request a resend.
            log.error("Sending mail to {} failed: {}", to, e.getMessage());
        }
    }
}
