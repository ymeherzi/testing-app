package com.tengames.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback used when no mail provider is configured.
 *
 * <p>By default it records only that a message was sent — never its body,
 * because that body contains a login code and anyone with log access could
 * use it to take over an account. Set {@code app.mail.log-codes=true}
 * (env {@code MAIL_LOG_CODES}) to print codes while testing without a real
 * inbox; remove it before real users sign up.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final boolean logCodes;

    public LoggingEmailSender(boolean logCodes) {
        this.logCodes = logCodes;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (logCodes) {
            log.warn("""
                    No email provider configured — printing the message instead.
                    THIS EXPOSES A LOGIN CODE: unset MAIL_LOG_CODES before real users sign up.
                      to: {}
                      subject: {}
                      {}""", to, subject, body);
        } else {
            log.warn("No email provider configured — '{}' to {} was not delivered "
                    + "(set RESEND_API_KEY to send it, or MAIL_LOG_CODES=true to print it while testing)",
                    subject, to);
        }
    }
}
