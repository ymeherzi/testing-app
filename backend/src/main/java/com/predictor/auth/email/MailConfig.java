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
            log.warn("No RESEND_API_KEY set — verification codes will be written to the log, not emailed");
            return new LoggingEmailSender();
        }
        return new ResendEmailSender(restClientBuilder, properties);
    }
}
