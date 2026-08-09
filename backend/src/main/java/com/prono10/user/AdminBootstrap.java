package com.prono10.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Production counterpart of the dev-profile bootstrap: creates the first
 * admin account from APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD so a fresh
 * deployment can be administered (fixture sync, gameweek curation) without
 * touching the database. No-op when the variables are unset or the account
 * already exists.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }
        User admin = new User(email, passwordEncoder.encode(password), "Admin", null, null);
        admin.setAdmin(true);
        admin.markEmailVerified(); // the operator set these credentials themselves
        users.save(admin);
        log.info("Admin bootstrap: created admin account {}", email);
    }
}
