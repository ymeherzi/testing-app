package com.predictor.fixtures;

import com.predictor.catalog.Competition;
import com.predictor.catalog.CompetitionRepository;
import com.predictor.catalog.Match;
import com.predictor.catalog.MatchRepository;
import com.predictor.catalog.Team;
import com.predictor.catalog.TeamRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FixtureSyncService {

    private static final Logger log = LoggerFactory.getLogger(FixtureSyncService.class);

    private final FixtureProvider provider;
    private final CompetitionRepository competitions;
    private final TeamRepository teams;
    private final MatchRepository matches;
    private final Clock clock;

    public FixtureSyncService(FixtureProvider provider, CompetitionRepository competitions,
                              TeamRepository teams, MatchRepository matches, Clock clock) {
        this.provider = provider;
        this.competitions = competitions;
        this.teams = teams;
        this.matches = matches;
        this.clock = clock;
    }

    public record SyncSummary(int teamsUpserted, int matchesUpserted) {
    }

    /** Sync all provider-backed competitions over a default window (past week to +30 days). */
    @Transactional
    public SyncSummary syncAll() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return syncAll(today.minusDays(7), today.plusDays(30));
    }

    @Transactional
    public SyncSummary syncAll(LocalDate from, LocalDate to) {
        int teamCount = 0;
        int matchCount = 0;
        for (Competition competition : competitions.findAll()) {
            if (competition.getProviderRef() == null) {
                continue;
            }
            SyncSummary summary = syncCompetition(competition, from, to);
            teamCount += summary.teamsUpserted();
            matchCount += summary.matchesUpserted();
        }
        log.info("Fixture sync complete: {} teams, {} matches", teamCount, matchCount);
        return new SyncSummary(teamCount, matchCount);
    }

    private SyncSummary syncCompetition(Competition competition, LocalDate from, LocalDate to) {
        Map<String, Team> teamsByRef = new HashMap<>();
        int teamCount = 0;
        for (ProviderTeam providerTeam : provider.fetchTeams(competition.getProviderRef())) {
            teamsByRef.put(providerTeam.providerRef(), upsertTeam(providerTeam));
            teamCount++;
        }
        int matchCount = 0;
        for (ProviderMatch providerMatch : provider.fetchMatches(competition.getProviderRef(), from, to)) {
            upsertMatch(competition, providerMatch, teamsByRef);
            matchCount++;
        }
        return new SyncSummary(teamCount, matchCount);
    }

    private Team upsertTeam(ProviderTeam providerTeam) {
        Team team = teams.findByProviderRef(providerTeam.providerRef())
                .orElseGet(() -> teams.save(new Team(providerTeam.name(), providerTeam.shortName(),
                        providerTeam.crestUrl(), providerTeam.providerRef())));
        team.setName(providerTeam.name());
        team.setShortName(providerTeam.shortName());
        team.setCrestUrl(providerTeam.crestUrl());
        return team;
    }

    private void upsertMatch(Competition competition, ProviderMatch providerMatch, Map<String, Team> teamsByRef) {
        Team home = resolveTeam(providerMatch.homeTeam(), teamsByRef);
        Team away = resolveTeam(providerMatch.awayTeam(), teamsByRef);
        Match match = matches.findByProviderRef(providerMatch.providerRef())
                .orElseGet(() -> matches.save(new Match(competition, home, away,
                        providerMatch.kickoffUtc(), providerMatch.status(), providerMatch.providerRef())));
        match.setKickoffUtc(providerMatch.kickoffUtc());
        match.setStatus(providerMatch.status());
        match.setScore(providerMatch.homeScore(), providerMatch.awayScore());
        match.setLastSyncedAt(clock.instant());
    }

    private Team resolveTeam(ProviderTeam providerTeam, Map<String, Team> teamsByRef) {
        Team known = teamsByRef.get(providerTeam.providerRef());
        if (known != null) {
            return known;
        }
        Team team = upsertTeam(providerTeam);
        teamsByRef.put(providerTeam.providerRef(), team);
        return team;
    }
}
