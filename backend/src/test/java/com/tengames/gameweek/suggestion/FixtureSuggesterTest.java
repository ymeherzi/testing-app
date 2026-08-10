package com.tengames.gameweek.suggestion;

import com.tengames.catalog.Competition;
import com.tengames.catalog.Match;
import com.tengames.catalog.Team;
import com.tengames.catalog.MatchStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The card an editor is offered decides what the whole league plays. What
 * matters is that the unpredictable fixtures float to the top and that no
 * single league takes the whole page.
 */
class FixtureSuggesterTest {

    private static Instant clock = Instant.parse("2026-08-21T18:00:00Z");

    private static Team team(String name) {
        return new Team(name, name, null, "test:" + name);
    }

    private static Match match(String code, String home, String away) {
        clock = clock.plusSeconds(3600);
        return new Match(new Competition(code, code), team(home), team(away), clock,
                MatchStatus.SCHEDULED, "test:" + home + ":" + away);
    }

    private static List<String> labels(List<FixtureSuggester.Suggestion> suggestions) {
        List<String> out = new ArrayList<>();
        for (FixtureSuggester.Suggestion s : suggestions) {
            out.add(s.match().getHomeTeam().getName() + " v " + s.match().getAwayTeam().getName());
        }
        return out;
    }

    @Test
    void aDerbyOutranksAnyOtherFixture() {
        List<Match> pool = List.of(
                match("PL", "Brighton", "Brentford"),
                match("PL", "Arsenal", "Tottenham Hotspur"),
                match("PL", "Liverpool", "Wolves"));

        List<FixtureSuggester.Suggestion> picked = FixtureSuggester.suggest(pool, 3);

        assertThat(labels(picked).getFirst()).isEqualTo("Arsenal v Tottenham Hotspur");
        assertThat(picked.getFirst().reasons()).contains("derby");
    }

    @Test
    void twoBigClubsBeatOneBigClubBeatsNeither() {
        List<Match> pool = List.of(
                match("PL", "Burnley", "Luton Town"),
                match("PL", "Chelsea", "Burnley"),
                match("PL", "Manchester City", "Liverpool"));

        assertThat(labels(FixtureSuggester.suggest(pool, 3)))
                .containsExactly("Manchester City v Liverpool", "Chelsea v Burnley", "Burnley v Luton Town");
    }

    @Test
    void noSingleCompetitionTakesOverTheCard() {
        List<Match> pool = new ArrayList<>();
        // six heavyweight English games would otherwise sweep the top of the list
        pool.add(match("PL", "Arsenal", "Chelsea"));
        pool.add(match("PL", "Liverpool", "Manchester City"));
        pool.add(match("PL", "Manchester United", "Newcastle United"));
        pool.add(match("PL", "Aston Villa", "Tottenham Hotspur"));
        pool.add(match("PD", "Real Madrid", "Sevilla"));
        pool.add(match("SA", "Napoli", "Atalanta"));

        List<FixtureSuggester.Suggestion> picked = FixtureSuggester.suggest(pool, 5);

        long english = picked.stream().filter(s -> s.match().getCompetition().getCode().equals("PL")).count();
        assertThat(english).isEqualTo(3);
        assertThat(picked).hasSize(5);
    }

    @Test
    void aThinWeekStillReturnsAFullCard() {
        List<Match> pool = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pool.add(match("PL", "Home " + i, "Away " + i));
        }

        // the variety cap must not silently hand back three fixtures
        assertThat(FixtureSuggester.suggest(pool, 5)).hasSize(5);
    }

    @Test
    void clubNamesAreMatchedDespiteSpellingDifferences() {
        assertThat(BigMatches.isElite("FC Bayern München")).isTrue();
        assertThat(BigMatches.isElite("Atlético de Madrid")).isTrue();
        assertThat(BigMatches.isRivalry("AC Milan", "Inter")).isTrue();
        assertThat(BigMatches.isElite("Luton Town")).isFalse();
        // "club" is stripped from incoming names, so the reference data must be
        // normalised the same way or this entry silently never matches
        assertThat(BigMatches.isElite("Athletic Club")).isTrue();
        assertThat(BigMatches.isRivalry("Real Sociedad", "Athletic Club")).isTrue();
        assertThat(BigMatches.isRivalry("Atlético de Madrid", "Real Madrid")).isTrue();
    }
}
