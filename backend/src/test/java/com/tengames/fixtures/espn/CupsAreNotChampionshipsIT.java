package com.tengames.fixtures.espn;

import com.tengames.TestcontainersConfiguration;
import com.tengames.catalog.Competition;
import com.tengames.catalog.CompetitionRepository;
import com.tengames.catalog.MatchRepository;
import com.tengames.catalog.TeamRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Whether an imported competition is a championship.
 *
 * <p>V13 marked the cups as not-a-league, and it was true of every row that
 * existed when it ran. The cups' rows are created later, by this importer, on
 * their first import — with the column at its default, which is true. The
 * favourite-championship dropdown duly offered the Coppa Italia, the DFB-Pokal
 * and the EFL Cup.
 *
 * <p>A migration cannot own this, because it only ever sees the past. The
 * importer stamps the flag on every import instead.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CupsAreNotChampionshipsIT {

    @Autowired
    private CompetitionRepository competitions;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private Clock clock;

    /** An importer whose ESPN answers are empty: the row is all we are after. */
    private EspnCupImporter importerFor(String... entries) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.manyTimes(), requestTo(containsString("scoreboard")))
                .andRespond(withSuccess("{\"events\":[]}", MediaType.APPLICATION_JSON));
        EspnProperties properties = new EspnProperties(true, "https://espn.example", null, List.of(entries));
        return new EspnCupImporter(builder.build(), properties, competitions, teams, matches, clock);
    }

    private Competition imported(String code, String... entries) {
        importerFor(entries).importCups(LocalDate.now(), LocalDate.now().plusDays(7));
        return competitions.findByCode(code).orElseThrow();
    }

    @Test
    void anImportedCupIsNotOfferedAsAChampionship() {
        assertThat(imported("TSTCUP", "ita.coppa_italia|TSTCUP|Test Cup").isDomestic()).isFalse();
    }

    @Test
    void anImportedLeagueIs() {
        assertThat(imported("TSTLGE", "bel.1|TSTLGE|Test League|league").isDomestic()).isTrue();
    }

    @Test
    void aRowAlreadyMarkedWrongIsPutRight() {
        // this is the state production is in: the row exists, and it is wrong
        Competition wrong = competitions.save(new Competition("TSTFIX", "Test Cup To Fix"));
        assertThat(wrong.isDomestic()).isTrue();

        assertThat(imported("TSTFIX", "eng.fa|TSTFIX|Test Cup To Fix").isDomestic()).isFalse();
    }

    @Test
    void aMalformedEntryIsStillIgnored() {
        importerFor("nonsense-without-any-pipes").importCups(LocalDate.now(), LocalDate.now().plusDays(7));

        assertThat(competitions.findByCode("nonsense-without-any-pipes")).isEmpty();
    }

    @Test
    void theCupsWeShipAreAllMarkedAsCups() {
        // the configured list is the source of truth for the dropdown, so a new
        // entry that forgets the marker is caught here rather than on a phone
        List<String> leagues = new EspnProperties(null, null, null, null).cups().stream()
                .filter(entry -> entry.endsWith("|league"))
                .toList();

        assertThat(leagues).containsExactlyInAnyOrder(
                "bel.1|BEL|Jupiler Pro League|league",
                "sco.1|SCO|Scottish Premiership|league");
    }
}
