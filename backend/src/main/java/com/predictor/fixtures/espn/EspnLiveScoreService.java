package com.predictor.fixtures.espn;

import com.predictor.catalog.Match;
import com.predictor.catalog.MatchRepository;
import com.predictor.catalog.MatchStatus;
import com.predictor.scoring.ScoringService;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Live scores for manually curated fixtures (design §6 manual mode +
 * live matchday). ESPN's public scoreboard needs no key and covers the
 * leagues our manual fixtures come from. Matches are correlated by
 * normalized team names + same-day kickoff, then pinned via
 * provider_ref = espn:{eventId}. Finished matches trigger scoring.
 */
@Service
public class EspnLiveScoreService {

    private static final Logger log = LoggerFactory.getLogger(EspnLiveScoreService.class);

    private static final EnumSet<MatchStatus> UPDATABLE = EnumSet.of(
            MatchStatus.SCHEDULED, MatchStatus.TIMED, MatchStatus.IN_PLAY,
            MatchStatus.PAUSED, MatchStatus.SUSPENDED);

    private final MatchRepository matches;
    private final ScoringService scoringService;
    private final EspnProperties properties;
    private final RestClient restClient;
    private final org.springframework.transaction.support.TransactionTemplate tx;
    private final Clock clock;

    public EspnLiveScoreService(MatchRepository matches, ScoringService scoringService,
                                EspnProperties properties, RestClient.Builder restClientBuilder,
                                org.springframework.transaction.support.TransactionTemplate tx, Clock clock) {
        this.matches = matches;
        this.scoringService = scoringService;
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.tx = tx;
        this.clock = clock;
    }

    public record PollSummary(int tracked, int updated, int finished) {
    }

    /**
     * Update all manual matches that could be producing a score right now
     * (kicked off, not settled). Self-suppresses when nothing is active.
     */
    public PollSummary poll() {
        Instant now = clock.instant();
        List<Match> active = matches.findWithTeamsByStatusInAndKickoffUtcBefore(UPDATABLE, now).stream()
                .filter(m -> m.getProviderRef() == null || m.getProviderRef().startsWith("espn:"))
                .filter(m -> m.getKickoffUtc().isAfter(now.minus(Duration.ofHours(8))))
                .toList();
        if (active.isEmpty()) {
            return new PollSummary(0, 0, 0);
        }
        List<EspnEvent> events = fetchEvents(active);
        int updated = 0;
        int finished = 0;
        for (Match match : active) {
            EspnEvent event = correlate(match, events);
            if (event == null) {
                continue;
            }
            boolean becameFinal = applyEvent(match.getId(), event);
            updated++;
            if (becameFinal) {
                finished++;
            }
        }
        log.info("ESPN poll: {} active manual matches, {} updated, {} finished", active.size(), updated, finished);
        return new PollSummary(active.size(), updated, finished);
    }

    private boolean applyEvent(long matchId, EspnEvent event) {
        boolean becameFinal = Boolean.TRUE.equals(tx.execute(status -> {
            Match match = matches.findById(matchId).orElseThrow();
            if (match.getProviderRef() == null) {
                match.setProviderRef("espn:" + event.id());
            }
            match.setScore(event.homeScore(), event.awayScore());
            MatchStatus newStatus = switch (event.state()) {
                case "in" -> MatchStatus.IN_PLAY;
                case "post" -> MatchStatus.FINISHED;
                default -> match.getStatus();
            };
            boolean turnedFinal = newStatus.isFinal() && !match.getStatus().isFinal();
            match.setStatus(newStatus);
            match.setLastSyncedAt(clock.instant());
            return turnedFinal;
        }));
        if (becameFinal) {
            Match refreshed = matches.findById(matchId).orElseThrow();
            if (refreshed.hasResult()) {
                scoringService.scoreMatch(matchId);
            }
        }
        return becameFinal;
    }

    private List<EspnEvent> fetchEvents(List<Match> active) {
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (Match match : active) {
            dates.add(LocalDate.ofInstant(match.getKickoffUtc(), ZoneOffset.UTC));
        }
        List<EspnEvent> events = new ArrayList<>();
        for (String league : properties.leagues()) {
            for (LocalDate date : dates) {
                try {
                    ScoreboardResponse response = restClient.get()
                            .uri("/{league}/scoreboard?dates={date}", league,
                                    date.format(DateTimeFormatter.BASIC_ISO_DATE))
                            .retrieve()
                            .body(ScoreboardResponse.class);
                    if (response != null && response.events() != null) {
                        response.events().forEach(e -> {
                            EspnEvent parsed = e.parse();
                            if (parsed != null) {
                                events.add(parsed);
                            }
                        });
                    }
                } catch (Exception e) {
                    log.warn("ESPN scoreboard fetch failed for {} {}: {}", league, date, e.getMessage());
                }
            }
        }
        return events;
    }

    private EspnEvent correlate(Match match, List<EspnEvent> events) {
        String ref = match.getProviderRef();
        if (ref != null) {
            String id = ref.substring("espn:".length());
            for (EspnEvent event : events) {
                if (event.id().equals(id)) {
                    return event;
                }
            }
            return null;
        }
        String home = normalize(match.getHomeTeam().getName());
        String away = normalize(match.getAwayTeam().getName());
        for (EspnEvent event : events) {
            if (event.kickoff() != null
                    && normalize(event.homeName()).equals(home) && normalize(event.awayName()).equals(away)
                    && Duration.between(match.getKickoffUtc(), event.kickoff()).abs().toHours() <= 6) {
                return event;
            }
        }
        return null;
    }

    static String normalize(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "");
    }

    public record EspnEvent(String id, String homeName, String awayName, Instant kickoff,
                            String state, Integer homeScore, Integer awayScore) {
    }

    // --- wire format ---

    record ScoreboardResponse(List<EventJson> events) {
    }

    record EventJson(String id, String date, StatusJson status, List<CompetitionJson> competitions) {

        /** ESPN sends minute-precision timestamps ("…T16:00Z") that Instant.parse rejects. */
        private static Instant parseDate(String date) {
            if (date == null) {
                return null;
            }
            try {
                return Instant.parse(date);
            } catch (java.time.format.DateTimeParseException e) {
                try {
                    return java.time.OffsetDateTime.parse(date,
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]X")).toInstant();
                } catch (java.time.format.DateTimeParseException ignored) {
                    return null;
                }
            }
        }

        EspnEvent parse() {
            if (competitions == null || competitions.isEmpty() || competitions.getFirst().competitors() == null) {
                return null;
            }
            CompetitorJson home = null;
            CompetitorJson away = null;
            for (CompetitorJson competitor : competitions.getFirst().competitors()) {
                if ("home".equals(competitor.homeAway())) {
                    home = competitor;
                } else if ("away".equals(competitor.homeAway())) {
                    away = competitor;
                }
            }
            if (home == null || away == null) {
                return null;
            }
            String state = status != null && status.type() != null ? status.type().state() : "pre";
            return new EspnEvent(id, home.team().displayName(), away.team().displayName(), parseDate(date),
                    state, parseScore(home.score()), parseScore(away.score()));
        }

        private static Integer parseScore(String score) {
            try {
                return score == null ? null : Integer.valueOf(score);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    record CompetitionJson(List<CompetitorJson> competitors) {
    }

    record CompetitorJson(String homeAway, String score, TeamJson team) {
    }

    record TeamJson(String displayName) {
    }

    record StatusJson(StatusTypeJson type) {
    }

    record StatusTypeJson(String state) {
    }
}
