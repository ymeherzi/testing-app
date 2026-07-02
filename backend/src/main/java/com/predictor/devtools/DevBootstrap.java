package com.predictor.devtools;

import com.predictor.fixtures.FixtureSyncService;
import com.predictor.user.User;
import com.predictor.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Makes a fresh dev environment immediately playable: seeded fixtures and a
 * known admin account (admin@dev.local / admin123!).
 */
@Component
@Profile("dev")
public class DevBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevBootstrap.class);

    public static final String ADMIN_EMAIL = "admin@dev.local";
    public static final String ADMIN_PASSWORD = "admin123!";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final FixtureSyncService syncService;

    public DevBootstrap(UserRepository users, PasswordEncoder passwordEncoder, FixtureSyncService syncService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.syncService = syncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase(ADMIN_EMAIL)) {
            log.info("Dev bootstrap: admin user already present, refreshing fixtures only");
        } else {
            User admin = new User(ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), "Admin", null, null);
            admin.setAdmin(true);
            users.save(admin);
            log.info("Dev bootstrap: created admin user {} / {}", ADMIN_EMAIL, ADMIN_PASSWORD);
        }
        FixtureSyncService.SyncSummary summary = syncService.syncAll();
        log.info("Dev bootstrap: synced {} teams and {} matches", summary.teamsUpserted(), summary.matchesUpserted());
    }
}
