package com.tengames.fixtures;

import com.tengames.catalog.Competition;
import com.tengames.catalog.CompetitionRepository;
import com.tengames.catalog.Match;
import com.tengames.catalog.MatchRepository;
import com.tengames.catalog.Team;
import com.tengames.catalog.TeamRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FixtureSyncService {

    private static final Logger log = LoggerFactory.getLogger(FixtureSyncService.class);

    private final FixtureProvider provider;
    private final CompetitionRepository competitions;
    private final TeamRepository teams;
    private final MatchRepository matches;
    private final org.springframework.transaction.support.TransactionTemplate competitionTx;
    private final Clock clock;

    public FixtureSyncService(FixtureProvider provider, CompetitionRepository competitions,
                              TeamRepository teams, MatchRepository matches,
                              org.springframework.transaction.PlatformTransactionManager txManager, Clock clock) {
        this.provider = provider;
        this.competitions = competitions;
        this.teams = teams;
        this.matches = matches;
        this.clock = clock;
        this.competitionTx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.competitionTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @param failed competitions the provider could not deliver, by code. An
     *               empty list is the only completely successful outcome, and
     *               the editor needs to see the difference.
     */
    public record SyncSummary(int teamsUpserted, int matchesUpserted, java.util.List<String> failed) {

        public SyncSummary(int teamsUpserted, int matchesUpserted) {
            this(teamsUpserted, matchesUpserted, java.util.List.of());
        }
    }

    /** Sync all provider-backed competitions over a default window (past week to +30 days). */
    public SyncSummary syncAll() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        return syncAll(today.minusDays(7), today.plusDays(30));
    }

    public SyncSummary syncAll(LocalDate from, LocalDate to) {
        return syncAll(from, to, true);
    }

    /**
     * Syncs every provider-backed competition, one at a time.
     *
     * <p>Deliberately not one transaction over the lot, and deliberately not
     * abandoned at the first error. The free tier answers 429 when the minute's
     * allowance is spent, and a single one of those used to abort the whole
     * run, roll back everything already fetched, and — because the cup import
     * runs after this — leave the Community Shield and the Trophée des
     * Champions out of the pool entirely. One competition failing now costs
     * that competition and nothing else.
     */
    /**
     * @param includeTeams whether to pull each competition's squad list too.
     *                     It doubles the number of calls, and the free tier
     *                     allows ten a minute: with a dozen competitions that
     *                     is the difference between a one-minute sync and a
     *                     three-minute one. Fixtures already carry their two
     *                     clubs and their crests, so this is only needed to
     *                     keep the club list complete for the profile picker —
     *                     once a day is plenty.
     */
    public SyncSummary syncAll(LocalDate from, LocalDate to, boolean includeTeams) {
        int teamCount = 0;
        int matchCount = 0;
        java.util.List<String> failed = new java.util.ArrayList<>();
        for (Competition competition : competitions.findAll()) {
            if (competition.getProviderRef() == null) {
                continue;
            }
            try {
                // an explicit transaction, not @Transactional: this is a call
                // from inside the bean, which never reaches the proxy
                SyncSummary summary = competitionTx.execute(status -> syncCompetition(
                        competitions.findById(competition.getId()).orElseThrow(), from, to, includeTeams));
                teamCount += summary.teamsUpserted();
                matchCount += summary.matchesUpserted();
            } catch (RuntimeException e) {
                failed.add(competition.getCode());
                log.warn("Sync failed for {} — keeping what the other competitions returned: {}",
                        competition.getCode(), e.getMessage());
            }
        }
        if (failed.isEmpty()) {
            log.info("Fixture sync complete: {} teams, {} matches", teamCount, matchCount);
        } else {
            log.warn("Fixture sync partial: {} teams, {} matches, failed for {}", teamCount, matchCount, failed);
        }
        return new SyncSummary(teamCount, matchCount, java.util.List.copyOf(failed));
    }

    /** One competition; the caller runs it in a transaction of its own. */
    private SyncSummary syncCompetition(Competition competition, LocalDate from, LocalDate to,
                                        boolean includeTeams) {
        Map<String, Team> teamsByRef = new HashMap<>();
        int teamCount = 0;
        if (includeTeams) {
            for (ProviderTeam providerTeam : provider.fetchTeams(competition.getProviderRef())) {
                Team team = upsertTeam(providerTeam);
                if (competition.isDomestic()) {
                    // this list is the definition of "the clubs in this league
                    // this season"; a cup's list would overwrite it with clubs
                    // that belong somewhere else
                    team.setPrimaryCompetitionId(competition.getId());
                }
                teamsByRef.put(providerTeam.providerRef(), team);
                teamCount++;
            }
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
        if (providerTeam.tla() != null) {
            team.setTla(providerTeam.tla());
        }
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
