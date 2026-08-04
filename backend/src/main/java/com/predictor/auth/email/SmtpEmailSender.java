package com.predictor.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Any SMTP relay — Brevo, Mailjet, Gmail. Preferred over the Resend adapter
 * because these providers verify a single sender address rather than a whole
 * domain, so they can reach any recipient without owning a domain first.
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (RuntimeException e) {
            // The relay's own words ("sender not verified", "auth failed") are
            // the only clue to what went wrong, and the user never sees them.
            String detail = "Sending mail to %s failed: %s".formatted(to, e.getMessage());
            log.error(detail);
            throw new MailDeliveryException(detail, e);
        }
    }
}
