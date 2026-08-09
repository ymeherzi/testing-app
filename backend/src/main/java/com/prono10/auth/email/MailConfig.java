package com.prono10.auth.email;

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestClient;

@Configuration
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /**
     * An SMTP relay wins when one is configured: providers like Brevo verify a
     * single sender address, so they deliver to anybody, while Resend needs a
     * whole verified domain first. With neither, codes go to the log so the
     * flow still works — and a blank env var must not count as configured.
     */
    @Bean
    public EmailSender emailSender(MailProperties properties, RestClient.Builder restClientBuilder) {
        if (properties.smtp().configured()) {
            return new SmtpEmailSender(mailSender(properties.smtp()), properties.from());
        }
        if (properties.resendApiKey() != null && !properties.resendApiKey().isBlank()) {
            return new ResendEmailSender(restClientBuilder, properties);
        }
        if (properties.logCodes()) {
            log.warn("MAIL_LOG_CODES is on: login codes are being written to the log. "
                    + "Turn this off before real users sign up.");
        } else {
            log.warn("No mail provider configured — verification emails cannot be delivered");
        }
        return new LoggingEmailSender(properties.logCodes());
    }

    private static JavaMailSenderImpl mailSender(MailProperties.Smtp smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.host());
        sender.setPort(smtp.port());
        sender.setUsername(smtp.username());
        sender.setPassword(smtp.password());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(smtp.username() != null && !smtp.username().isBlank()));
        // 465 is implicit TLS; everything else negotiates it with STARTTLS
        properties.put("mail.smtp.ssl.enable", String.valueOf(smtp.port() == 465));
        properties.put("mail.smtp.starttls.enable", String.valueOf(smtp.port() != 465));
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
