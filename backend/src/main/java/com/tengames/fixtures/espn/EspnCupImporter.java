package com.tengames.fixtures.espn;

import com.tengames.catalog.Competition;
import com.tengames.catalog.CompetitionRepository;
import com.tengames.catalog.Match;
import com.tengames.catalog.MatchRepository;
import com.tengames.catalog.MatchStatus;
import com.tengames.catalog.Team;
import com.tengames.catalog.TeamRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Imports the fixtures football-data's free tier does not carry.
 *
 * <p>The season opens with two finals nobody would want to miss — the
 * Community Shield and the Trophée des Champions — and neither appears in
 * the thirteen competitions the free plan exposes. ESPN publishes both,
 * without a key, on the same scoreboard the live scores already come from.
 *
 * <p>Matches are stored with the {@code espn:} reference the live poller
 * correlates on, so an imported cup final follows its own score with no
 * further wiring.
 */
@Component
@ConditionalOnProperty(name = "app.espn.enabled", havingValue = "true", matchIfMissing = true)
public class EspnCupImporter {

    private static final Logger log = LoggerFactory.getLogger(EspnCupImporter.class);

    private final RestClient restClient;
    private final EspnProperties properties;
    private final CompetitionRepository competitions;
    private final TeamRepository teams;
    private final MatchRepository matches;
    private final Clock clock;

    public EspnCupImporter(RestClient espnRestClient, EspnProperties properties,
                           CompetitionRepository competitions, TeamRepository teams,
                           MatchRepository matches, Clock clock) {
        // the shared client: ESPN's edge refuses the JDK's default User-Agent
        this.restClient = espnRestClient;
        this.properties = properties;
        this.competitions = competitions;
        this.teams = teams;
        this.matches = matches;
        this.clock = clock;
    }

    /** One configured cup: the ESPN slug, our code, and the display name. */
    private record Cup(String slug, String code, String name) {

        static Cup parse(String configured) {
            String[] parts = configured.split("\\|");
            return parts.length == 3 ? new Cup(parts[0].trim(), parts[1].trim(), parts[2].trim()) : null;
        }
    }

    @Transactional
    public int importCups(LocalDate from, LocalDate to) {
        int imported = 0;
        for (String configured : properties.cups()) {
            Cup cup = Cup.parse(configured);
            if (cup == null) {
                log.warn("Ignoring malformed cup entry '{}' (expected slug|CODE|Name)", configured);
                continue;
            }
            imported += importCup(cup, from, to);
        }
        return imported;
    }

    private int importCup(Cup cup, LocalDate from, LocalDate to) {
        List<EspnLiveScoreService.EventJson> events;
        try {
            var response = restClient.get()
                    .uri("/{league}/scoreboard?dates={from}-{to}", cup.slug(),
                            from.format(DateTimeFormatter.BASIC_ISO_DATE),
                            to.format(DateTimeFormatter.BASIC_ISO_DATE))
                    .retrieve()
                    .body(EspnLiveScoreService.ScoreboardResponse.class);
            events = response == null || response.events() == null ? List.of() : response.events();
        } catch (Exception e) {
            // A cup that fails to load must not take the whole sync with it.
            log.warn("ESPN cup import failed for {}: {}", cup.slug(), e.getMessage());
            return 0;
        }

        Competition competition = competitionFor(cup);
        int count = 0;
        for (EspnLiveScoreService.EventJson event : events) {
            if (upsert(competition, event)) {
                count++;
            }
        }
        if (count > 0) {
            log.info("Imported {} fixture(s) for {}", count, cup.name());
        }
        return count;
    }

    /**
     * The competition row, created on demand with no provider reference:
     * that is what keeps the football-data sync from trying to fetch it.
     */
    private Competition competitionFor(Cup cup) {
        return competitions.findByCode(cup.code())
                .orElseGet(() -> competitions.save(new Competition(cup.code(), cup.name())));
    }

    private boolean upsert(Competition competition, EspnLiveScoreService.EventJson event) {
        var parsed = event.parse();
        if (parsed == null || parsed.kickoff() == null) {
            return false;
        }
        var competitors = event.competitions().getFirst().competitors();
        EspnLiveScoreService.TeamJson home = null;
        EspnLiveScoreService.TeamJson away = null;
        for (var competitor : competitors) {
            if ("home".equals(competitor.homeAway())) {
                home = competitor.team();
            } else if ("away".equals(competitor.homeAway())) {
                away = competitor.team();
            }
        }
        if (home == null || away == null) {
            return false;
        }

        String ref = "espn:" + event.id();
        Team homeTeam = teamFor(home);
        Team awayTeam = teamFor(away);
        Match match = matches.findByProviderRef(ref)
                .orElseGet(() -> matches.save(new Match(competition, homeTeam, awayTeam,
                        parsed.kickoff(), status(parsed.state()), ref)));
        match.setKickoffUtc(parsed.kickoff());
        match.setStatus(status(parsed.state()));
        match.setScore(parsed.homeScore(), parsed.awayScore());
        match.setLastSyncedAt(clock.instant());
        return true;
    }

    private static MatchStatus status(String state) {
        return switch (state) {
            case "in" -> MatchStatus.IN_PLAY;
            case "post" -> MatchStatus.FINISHED;
            default -> MatchStatus.TIMED;
        };
    }

    /**
     * The club behind a cup entry, reusing the one the league import already
     * created rather than storing a twin.
     *
     * <p>Two sources spell clubs differently, so the match is made on a
     * normalised key: "Arsenal FC" and "Arsenal" are one club. The key is
     * deliberately conservative — Manchester City and Manchester United must
     * stay apart — and an unrecognised name simply becomes a new club, which
     * is the safe direction to be wrong in.
     */
    private Team teamFor(EspnLiveScoreService.TeamJson team) {
        String ref = "espn:team:" + team.id();
        return teams.findByProviderRef(ref)
                .or(() -> matchByName(team.displayName()))
                .map(existing -> {
                    if (existing.getCrestUrl() == null) {
                        existing.setCrestUrl(team.logo());
                    }
                    return existing;
                })
                .orElseGet(() -> teams.save(new Team(team.displayName(),
                        team.shortDisplayName() != null ? team.shortDisplayName() : team.displayName(),
                        team.logo(), ref)));
    }

    /**
     * Scanning the whole catalogue is fine here: it holds a few hundred clubs
     * and this runs once a day, which is cheaper than carrying a normalised
     * column and its index for one caller.
     */
    private java.util.Optional<Team> matchByName(String displayName) {
        String key = com.tengames.catalog.ClubNames.key(displayName);
        if (key.isEmpty()) {
            return java.util.Optional.empty();
        }
        return teams.findAll().stream()
                .filter(candidate -> com.tengames.catalog.ClubNames.key(candidate.getName()).equals(key))
                .findFirst();
    }
}
