package com.predictor.auth.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.mail")
public record MailProperties(String resendApiKey, String from, Boolean logCodes) {

    public MailProperties {
        if (from == null || from.isBlank()) {
            from = "Predictor <onboarding@resend.dev>";
        }
        if (logCodes == null) {
            logCodes = false; // never print login codes unless explicitly asked
        }
    }
}
