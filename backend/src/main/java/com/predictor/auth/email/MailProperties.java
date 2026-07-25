package com.predictor.auth.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.mail")
public record MailProperties(String resendApiKey, String from, Boolean logCodes) {

    public MailProperties {
        if (logCodes == null) {
            logCodes = false; // never print login codes unless explicitly asked
        }
        if (isSet(resendApiKey) && !isSet(from)) {
            // Resend's shared onboarding@resend.dev sender may only mail the
            // account owner, so defaulting to it would reject every real
            // recipient at send time. Better to say so at startup.
            throw new IllegalStateException(
                    "RESEND_API_KEY is set but MAIL_FROM is not. Set MAIL_FROM to an address on a "
                    + "domain verified at resend.com/domains, e.g. \"Warga <no-reply@example.com>\".");
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
