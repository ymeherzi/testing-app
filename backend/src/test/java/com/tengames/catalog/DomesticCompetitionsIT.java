package com.tengames.catalog;

import com.tengames.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "My favourite championship" is a list of leagues. Nobody supports the Coupe
 * de France, and the league that would follow from supporting one makes no
 * sense either — which is what the dropdown offered until the flag was fixed.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class DomesticCompetitionsIT {

    /** Every code the migrations mark as not-a-league. */
    private static final List<String> NOT_LEAGUES =
            List.of("CL", "CSHIELD", "TDC", "FACUP", "EFLCUP", "CDR", "COPPA", "DFB", "CDF");

    @Autowired
    private CompetitionController controller;

    @Autowired
    private CompetitionRepository competitions;

    private List<String> championshipCodes() {
        return controller.list(true).stream().map(CompetitionController.CompetitionResponse::code).toList();
    }

    @Test
    void aCupIsNeverOfferedAsAChampionship() {
        assertThat(championshipCodes()).doesNotContainAnyElementsOf(NOT_LEAGUES);
    }

    @Test
    void everyCupRowThatExistsIsMarkedAsOne() {
        // the migrations own the rows already in the database; the importer owns
        // the ones it creates later
        List<Competition> present = NOT_LEAGUES.stream()
                .map(competitions::findByCode)
                .flatMap(java.util.Optional::stream)
                .toList();

        assertThat(present).isNotEmpty().noneMatch(Competition::isDomestic);
    }

    @Test
    void theLeaguesAreStillThere() {
        assertThat(championshipCodes()).contains("PL", "PD", "SA", "BL1", "FL1");
    }

    @Test
    void withoutTheFlagTheWholeCatalogueComesBack() {
        // the club picker's own filter asks for leagues too, but the unfiltered
        // list is what the rest of the app reads
        assertThat(controller.list(null).size()).isGreaterThan(championshipCodes().size());
    }
}
