package com.predictor.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Fallback used whenever no mail provider is configured: the code goes to
 * the application log instead of an inbox. Keeps the whole verification
 * flow usable in dev and on a fresh deployment before an API key exists.
 */
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.warn("""
                No email provider configured — printing the message instead.
                  to: {}
                  subject: {}
                  {}""", to, subject, body);
    }
}
