package com.tengames;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TenGamesApplicationTests {

    @Test
    void contextLoadsAndMigrationsApply() {
        // Boots the full application context against a throwaway Postgres,
        // which also runs the Flyway migrations.
    }
}
