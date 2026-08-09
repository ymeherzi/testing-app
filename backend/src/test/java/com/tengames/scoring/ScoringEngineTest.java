package com.tengames.scoring;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringEngineTest {

    @ParameterizedTest(name = "predicted {0}-{1}, actual {2}-{3} → {4} points")
    @CsvSource({
            // exact scorelines
            "2, 1, 2, 1, 3",
            "0, 0, 0, 0, 3",
            "1, 1, 1, 1, 3",
            "0, 3, 0, 3, 3",
            // correct goal difference, wins only
            "2, 1, 3, 2, 2",
            "1, 0, 2, 1, 2",
            "0, 2, 1, 3, 2",
            // correct outcome only
            "2, 0, 1, 0, 1",
            "3, 1, 1, 0, 1",
            "0, 1, 1, 3, 1",
            // the draw rule: drawn draw with wrong scoreline is 1, never 2
            "1, 1, 2, 2, 1",
            "0, 0, 3, 3, 1",
            "2, 2, 0, 0, 1",
            // wrong outcome
            "2, 1, 1, 2, 0",
            "1, 1, 2, 1, 0",
            "2, 1, 1, 1, 0",
            "0, 2, 2, 2, 0",
            "1, 0, 0, 0, 0",
    })
    void scoresTheDesignDocCases(int ph, int pa, int ah, int aa, int expected) {
        assertThat(ScoringEngine.score(ph, pa, ah, aa)).isEqualTo(expected);
    }
}
