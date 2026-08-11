package com.tengames.catalog;

import com.tengames.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Several hundred clubs is a list nobody scrolls, so the search is the whole
 * feature. It has to cope with the names the providers really send: someone
 * typing "munich" is looking for the club football-data calls "FC Bayern
 * München", and someone typing "psg" is typing letters that appear nowhere in
 * "Paris Saint-Germain FC".
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ClubSearchIT {

    @Autowired
    private TeamController controller;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private CompetitionRepository competitions;

    private Long ligue1;

    @BeforeEach
    void setUp() {
        ligue1 = competitions.findByCode("FL1").orElseThrow().getId();
        club("Paris Saint-Germain FC", "PSG", ligue1);
        club("FC Bayern München", "FCB", null);
        club("Club Atlético de Madrid", "ATM", null);
        club("FC Internazionale Milano", "INT", null);
        club("Olympique de Marseille", "OM", ligue1);
        // a club that only ever reached us through a cup tie: no league, and
        // still choosable
        club("Preußen Münster", null, null);
    }

    private void club(String name, String tla, Long competitionId) {
        Team team = teams.findFirstByNameIgnoreCase(name)
                .orElseGet(() -> teams.save(new Team(name, name, null, "search:" + UUID.randomUUID())));
        team.setTla(tla);
        team.setPrimaryCompetitionId(competitionId);
        teams.save(team);
    }

    private List<String> search(String query) {
        return controller.list(query, null, null, null).stream().map(TeamController.TeamResponse::name).toList();
    }

    @Test
    void anAbbreviationFindsTheClub() {
        // "PSG" is in no part of the club's name; without the abbreviation
        // this search returns nothing at all
        assertThat(search("psg")).contains("Paris Saint-Germain FC");
        assertThat(search("PSG")).contains("Paris Saint-Germain FC");
    }

    @Test
    void accentsAndRegisteredNamesDoNotGetInTheWay() {
        assertThat(search("munich")).contains("FC Bayern München");
        assertThat(search("munchen")).contains("FC Bayern München");
        assertThat(search("atletico")).contains("Club Atlético de Madrid");
        assertThat(search("inter")).contains("FC Internazionale Milano");
        assertThat(search("marseille")).contains("Olympique de Marseille");
    }

    @Test
    void aCompetitionNarrowsTheList() {
        List<String> french = controller.list(null, ligue1, null, 200).stream()
                .map(TeamController.TeamResponse::name).toList();

        assertThat(french).contains("Paris Saint-Germain FC", "Olympique de Marseille");
        assertThat(french).doesNotContain("FC Internazionale Milano", "Preußen Münster");
    }

    @Test
    void aClubWithNoLeagueIsStillFound() {
        // it came from a cup, has no squad list to be stamped from, and
        // somebody may still support it
        assertThat(search("preußen")).contains("Preußen Münster");
        assertThat(search("preussen")).contains("Preußen Münster");
    }

    @Test
    void aQueryOfNothingButNoiseDoesNotReturnEverything() {
        // "de" is stripped by the normaliser; an empty needle used to match
        // every club in the catalogue
        assertThat(search("de")).allSatisfy(name -> assertThat(name.toLowerCase()).contains("de"));
        assertThat(search("zzzz")).isEmpty();
    }

    @Test
    void aClubCanBeFetchedById() {
        // the profile shows the club already chosen without searching for it;
        // without an id parameter this used to hand back the whole catalogue
        // and the caller took the first one — a different club every time
        Team psg = teams.findFirstByNameIgnoreCase("Paris Saint-Germain FC").orElseThrow();

        List<TeamController.TeamResponse> found = controller.list(null, null, psg.getId(), null);

        assertThat(found).singleElement()
                .extracting(TeamController.TeamResponse::name).isEqualTo("Paris Saint-Germain FC");
        assertThat(controller.list(null, null, 9_999_999L, null)).isEmpty();
    }

    @Test
    void theClosestMatchComesFirst() {
        assertThat(search("marseille").getFirst()).isEqualTo("Olympique de Marseille");
    }
}
