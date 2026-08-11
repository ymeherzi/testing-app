package com.tengames.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One club written two ways must collapse to one key; two clubs that merely
 * share a word must not. The second half is the dangerous one — merging
 * Manchester United into Manchester City would silently rewrite fixtures.
 */
class ClubNamesTest {

    private static void same(String one, String other) {
        assertThat(ClubNames.key(one)).isEqualTo(ClubNames.key(other));
    }

    private static void different(String one, String other) {
        assertThat(ClubNames.key(one)).isNotEqualTo(ClubNames.key(other));
    }

    @Test
    void spellingsOfTheSameClubAgree() {
        same("Arsenal FC", "Arsenal");
        same("Atlético de Madrid", "Atletico Madrid");
        same("FC Bayern München", "Bayern Munchen");
        same("AC Milan", "Milan");
        same("Athletic Club", "Athletic");
    }

    @Test
    void clubsSharingAWordStayApart() {
        different("Manchester United", "Manchester City");
        different("Real Madrid", "Real Sociedad");
        different("Real Betis", "Real Madrid");
        different("Borussia Dortmund", "Borussia Mönchengladbach");
        different("Inter", "Inter Miami");
    }

    @Test
    void anEmptyOrOddNameYieldsAnEmptyKey() {
        // an empty key must never be used to match, or every unnamed club
        // would be treated as the same one
        assertThat(ClubNames.key(null)).isEmpty();
        assertThat(ClubNames.key("   ")).isEmpty();
        assertThat(ClubNames.key("FC")).isEmpty();
    }
}
