package com.predictor.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /**
     * Real mail as soon as an API key exists; until then codes go to the
     * log so the flow still works (an empty env var must not count as
     * configured).
     */
    @Bean
    public EmailSender emailSender(MailProperties properties, RestClient.Builder restClientBuilder) {
        if (properties.resendApiKey() == null || properties.resendApiKey().isBlank()) {
            if (properties.logCodes()) {
                log.warn("MAIL_LOG_CODES is on: login codes are being written to the log. "
                        + "Turn this off before real users sign up.");
            } else {
                log.warn("No RESEND_API_KEY set — verification emails cannot be delivered");
            }
            return new LoggingEmailSender(properties.logCodes());
        }
        return new ResendEmailSender(restClientBuilder, properties);
    }
}
