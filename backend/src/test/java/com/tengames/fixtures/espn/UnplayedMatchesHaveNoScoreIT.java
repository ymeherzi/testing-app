package com.tengames.fixtures.espn;

import com.tengames.TestcontainersConfiguration;
import com.tengames.catalog.Match;
import com.tengames.catalog.MatchRepository;
import com.tengames.catalog.MatchStatus;
import com.tengames.catalog.TeamRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ESPN answers "0" for the score of a match that has not kicked off. Stored as
 * a result, that made every cup fixture in the pool look like a goalless draw
 * days early: the card showed "0 : 0" the moment the match locked, and offered
 * a running points total before a ball was kicked.
 *
 * <p>Proved against the real payload shape — this is exactly what
 * {@code eng.charity} returned for Arsenal v Manchester City on 16 August,
 * five days before it is played.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UnplayedMatchesHaveNoScoreIT {

    @Autowired
    private com.tengames.catalog.CompetitionRepository competitions;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Clock clock;

    /**
     * The importer is built by hand here, so Spring's @Transactional never
     * applies to it: without this the mutations after the initial save happen
     * on a detached entity and are quietly lost.
     */
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate tx;

    private static String scoreboard(String eventId, String state, String detail) {
        return scoreboard(eventId, state, detail, "0", "0");
    }

    private static String scoreboard(String eventId, String state, String detail, String home, String away) {
        return """
                {"events":[{"id":"%s","date":"2026-08-16T14:00Z",
                  "status":{"type":{"state":"%s","name":"%s"}},
                  "competitions":[{"competitors":[
                    {"homeAway":"home","score":"%s","team":{"id":"1","displayName":"Test Arsenal","abbreviation":"ARS"}},
                    {"homeAway":"away","score":"%s","team":{"id":"2","displayName":"Test City","abbreviation":"MCI"}}
                  ]}]}]}""".formatted(eventId, state, detail, home, away);
    }

    private Match imported(String body, String code) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.manyTimes(), requestTo(containsString("scoreboard")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        EspnProperties properties = new EspnProperties(true, "https://espn.example", null,
                List.of("eng.charity|%s|Test Shield".formatted(code)));
        EspnCupImporter importer = new EspnCupImporter(builder.build(), properties, competitions,
                teams, matches, clock);
        tx.executeWithoutResult(status ->
                importer.importCups(LocalDate.now(), LocalDate.now().plusDays(7)));
        return matches.findByProviderRef("espn:" + code + "-event").orElseThrow();
    }

    @Test
    void aFixtureThatHasNotKickedOffCarriesNoScore() {
        Match match = imported(scoreboard("SCHED-event", "pre", "STATUS_SCHEDULED"), "SCHED");

        assertThat(match.getHomeScore()).isNull();
        assertThat(match.getAwayScore()).isNull();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.TIMED);
    }

    @Test
    void aFinishedFixtureKeepsItsScore() {
        Match match = imported(scoreboard("DONE-event", "post", "STATUS_FULL_TIME", "2", "0"), "DONE");

        assertThat(match.getHomeScore()).isEqualTo(2);
        assertThat(match.getAwayScore()).isZero();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
    }

    @Test
    void noImportedFixtureCarriesAScoreItHasNotEarned() {
        // V17 cleared what was written before the guard existed. Scoped to the
        // rows ESPN owns: other tests deliberately put scores on matches of
        // their own, and this database is shared.
        Long stragglers = jdbc.sql("""
                        select count(*) from matches
                        where provider_ref like 'espn:%'
                          and status not in ('FINISHED', 'AWARDED')
                          and (home_score is not null or away_score is not null)""")
                .query(Long.class).single();

        assertThat(stragglers).isZero();
    }
}
