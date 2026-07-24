package com.predictor.auth.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.mail")
public record MailProperties(String resendApiKey, String from) {

    public MailProperties {
        if (from == null || from.isBlank()) {
            from = "Predictor <onboarding@resend.dev>";
        }
    }
}
