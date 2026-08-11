package com.tengames.gameweek.suggestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The editorial data is worth nothing if it does not match the names the two
 * providers actually send.
 *
 * <p>Every name below is verbatim from a real API response, checked on the day
 * this was written: football-data returns registered names ("FC
 * Internazionale Milano", "Olympique de Marseille"), ESPN returns the short
 * ones everybody uses. Fourteen elite clubs matched neither, so the Derby
 * della Madonnina, Le Classique, Ajax–Feyenoord and Benfica–Porto were listed
 * as rivalries the app could never recognise — a silent failure, since an
 * unrecognised fixture simply scores low and is never seen.
 */
class RealClubNamesTest {

    @Test
    void theBigClubsAreRecognisedUnderTheirRegisteredNames() {
        // football-data spellings, the ones that used to fail
        assertThat(BigMatches.isElite("FC Internazionale Milano")).isTrue();
        assertThat(BigMatches.isElite("Olympique de Marseille")).isTrue();
        assertThat(BigMatches.isElite("Olympique Lyonnais")).isTrue();
        assertThat(BigMatches.isElite("Sport Lisboa e Benfica")).isTrue();
        assertThat(BigMatches.isElite("Sporting Clube de Portugal")).isTrue();
        assertThat(BigMatches.isElite("Feyenoord Rotterdam")).isTrue();
        assertThat(BigMatches.isElite("Atalanta BC")).isTrue();
        assertThat(BigMatches.isElite("ACF Fiorentina")).isTrue();
        assertThat(BigMatches.isElite("Bayer 04 Leverkusen")).isTrue();
        assertThat(BigMatches.isElite("Real Sociedad de Fútbol")).isTrue();
        assertThat(BigMatches.isElite("Real Betis Balompié")).isTrue();
        assertThat(BigMatches.isElite("Lille OSC")).isTrue();
        assertThat(BigMatches.isElite("OGC Nice")).isTrue();
        assertThat(BigMatches.isElite("Racing Club de Lens")).isTrue();
    }

    @Test
    void theSameClubIsRecognisedUnderEspnsShorterName() {
        // the cups come from ESPN, so both spellings have to land on one club
        assertThat(BigMatches.isElite("Internazionale")).isTrue();
        assertThat(BigMatches.isElite("Bayern Munich")).isTrue();
        assertThat(BigMatches.isElite("Ajax Amsterdam")).isTrue();
        assertThat(BigMatches.isElite("PSV Eindhoven")).isTrue();
        assertThat(BigMatches.isElite("Marseille")).isTrue();
        assertThat(BigMatches.isElite("Benfica")).isTrue();
    }

    @Test
    void theDerbiesFireWhateverTheSourceCallsTheClubs() {
        // one side football-data, the other ESPN — exactly what happens when a
        // cup tie meets a league fixture
        assertThat(BigMatches.isRivalry("FC Internazionale Milano", "AC Milan")).isTrue();
        assertThat(BigMatches.isRivalry("Paris Saint-Germain FC", "Olympique de Marseille")).isTrue();
        assertThat(BigMatches.isRivalry("AFC Ajax", "Feyenoord Rotterdam")).isTrue();
        assertThat(BigMatches.isRivalry("Sport Lisboa e Benfica", "FC Porto")).isTrue();
        assertThat(BigMatches.isRivalry("Sevilla FC", "Real Betis Balompié")).isTrue();
        assertThat(BigMatches.isRivalry("Athletic Club", "Real Sociedad de Fútbol")).isTrue();
        assertThat(BigMatches.isRivalry("Celtic", "Rangers")).isTrue();
        assertThat(BigMatches.isRivalry("Anderlecht", "Club Brugge")).isTrue();
        assertThat(BigMatches.isRivalry("Heart of Midlothian", "Hibernian")).isTrue();
        // and the new second-tier ones, where most English derbies now live
        assertThat(BigMatches.isRivalry("Cardiff City FC", "Swansea City AFC")).isTrue();
        assertThat(BigMatches.isRivalry("West Bromwich Albion FC", "Wolverhampton Wanderers FC")).isTrue();
        assertThat(BigMatches.isRivalry("Portsmouth FC", "Southampton FC")).isTrue();
    }

    @Test
    void clubsThatMerelyShareAWordStayApart() {
        // the aliases must not become a way to merge two clubs
        assertThat(BigMatches.isRivalry("Manchester City FC", "Manchester United FC")).isTrue();
        assertThat(BigMatches.isElite("Charlton Athletic FC")).isFalse();
        assertThat(BigMatches.isElite("Sporting Clube de Braga")).isFalse();
        assertThat(BigMatches.isElite("RCD Espanyol de Barcelona")).isFalse();
        assertThat(BigMatches.isElite("Cercle Brugge KSV")).isFalse();
    }

    @Test
    void theNewCompetitionsAreRankedBelowTheBigFive() {
        // a Championship fixture must not outrank a Premier League one on the
        // badge alone; it earns its place through a derby
        assertThat(BigMatches.competitionWeight("ELC")).isLessThan(BigMatches.competitionWeight("PL"));
        assertThat(BigMatches.competitionWeight("BEL")).isLessThan(BigMatches.competitionWeight("PPL"));
        assertThat(BigMatches.competitionWeight("FACUP")).isLessThan(BigMatches.competitionWeight("CL"));
        assertThat(BigMatches.competitionWeight("EFLCUP")).isLessThan(BigMatches.competitionWeight("FACUP"));
    }
}
