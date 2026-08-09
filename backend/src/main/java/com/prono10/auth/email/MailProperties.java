package com.prono10.auth.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mail configuration. Two providers are supported and the sender address is
 * required with either, because both refuse mail from an address they don't
 * recognise — and they refuse it per message, long after startup.
 *
 * @param smtp        any SMTP relay (Brevo, Mailjet, Gmail…); preferred,
 *                    because such providers verify a single sender address
 *                    and can then reach any recipient
 * @param resendApiKey resend.com, which instead needs a whole verified
 *                    domain before it will mail anyone but the account owner
 */
@ConfigurationProperties("app.mail")
public record MailProperties(Smtp smtp, String resendApiKey, String from, Boolean logCodes) {

    public record Smtp(String host, Integer port, String username, String password) {

        public Smtp {
            if (port == null) {
                port = 587; // submission with STARTTLS, what every relay offers
            }
        }

        boolean configured() {
            return isSet(host);
        }
    }

    public MailProperties {
        if (smtp == null) {
            smtp = new Smtp(null, null, null, null);
        }
        if (logCodes == null) {
            logCodes = false; // never print login codes unless explicitly asked
        }
        if ((smtp.configured() || isSet(resendApiKey)) && !isSet(from)) {
            // Resend's shared onboarding@resend.dev sender may only mail the
            // account owner, and an SMTP relay rejects any sender it hasn't
            // verified. Either way a wrong or missing from address fails per
            // user at send time, so refuse to start instead.
            throw new IllegalStateException(
                    "A mail provider is configured but MAIL_FROM is not. Set MAIL_FROM to a sender "
                    + "the provider has verified, e.g. \"Warga <you@example.com>\".");
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
