package com.prono10.fixtures.espn;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EspnLiveScoreServiceTest {

    @Test
    void normalizationCollapsesAccentsCaseAndPunctuation() {
        assertThat(EspnLiveScoreService.normalize("Fortuna Düsseldorf"))
                .isEqualTo(EspnLiveScoreService.normalize("fortuna dusseldorf"));
        assertThat(EspnLiveScoreService.normalize("CF Montréal"))
                .isEqualTo(EspnLiveScoreService.normalize("cf montreal"));
        assertThat(EspnLiveScoreService.normalize("Inter Miami CF"))
                .isEqualTo("intermiamicf");
        assertThat(EspnLiveScoreService.normalize("St. Louis CITY SC"))
                .isEqualTo("stlouiscitysc");
    }
}
