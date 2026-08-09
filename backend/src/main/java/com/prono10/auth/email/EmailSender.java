package com.prono10.auth.email;

/** Port for outbound transactional mail (verification codes today). */
public interface EmailSender {

    void send(String to, String subject, String body);
}
