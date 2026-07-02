package com.predictor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PredictorApplicationTests {

    @Test
    void contextLoadsAndMigrationsApply() {
        // Boots the full application context against a throwaway Postgres,
        // which also runs the Flyway migrations.
    }
}
