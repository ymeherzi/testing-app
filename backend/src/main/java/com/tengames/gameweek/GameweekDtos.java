package com.tengames.gameweek;

import com.tengames.catalog.Match;
import com.tengames.catalog.Team;
import com.tengames.prediction.Prediction;
import java.time.Instant;
import java.util.List;

public final class GameweekDtos {

    private GameweekDtos() {
    }

    public record TeamView(Long id, String name, String shortName, String crestUrl) {

        public static TeamView from(Team team) {
            return new TeamView(team.getId(), team.getName(), team.getShortName(), team.getCrestUrl());
        }
    }

    public record PredictionView(int homeGoals, int awayGoals, Integer points) {

        public static PredictionView from(Prediction prediction) {
            return new PredictionView(prediction.getHomeGoals(), prediction.getAwayGoals(), prediction.getPoints());
        }
    }

    public record FixtureView(Long fixtureId, String competitionCode, String competitionName,
                              TeamView homeTeam, TeamView awayTeam, Instant kickoffUtc, String matchStatus,
                              Integer homeScore, Integer awayScore, boolean locked, PredictionView prediction) {

        public static FixtureView of(GameweekFixture fixture, boolean locked, Prediction prediction) {
            Match match = fixture.getMatch();
            return new FixtureView(
                    fixture.getId(),
                    match.getCompetition().getCode(),
                    match.getCompetition().getName(),
                    TeamView.from(match.getHomeTeam()),
                    TeamView.from(match.getAwayTeam()),
                    match.getKickoffUtc(),
                    match.getStatus().name(),
                    match.getHomeScore(),
                    match.getAwayScore(),
                    locked,
                    prediction == null ? null : PredictionView.from(prediction));
        }
    }

    public record GameweekView(Long id, String season, int weekIndex, String type, String status,
                               Instant windowStart, Instant windowEnd, List<FixtureView> fixtures) {

        public static GameweekView of(Gameweek gameweek, List<FixtureView> fixtures) {
            return new GameweekView(gameweek.getId(), gameweek.getSeason(), gameweek.getWeekIndex(),
                    gameweek.getType().name(), gameweek.getStatus().name(),
                    gameweek.getWindowStart(), gameweek.getWindowEnd(), fixtures);
        }
    }

    /** Row in the gameweek history list, with the caller's own return for it. */
    public record GameweekSummary(Long id, String season, int weekIndex, String type, String status,
                                  Instant windowStart, Instant windowEnd,
                                  int fixtureCount, long myPoints, int myPredictions) {
    }

    /** Another player's gameweek: picks are revealed only once a match locks. */
    public record PlayerGameweekView(java.util.UUID playerId, String displayName, String country,
                                     Long gameweekId, int weekIndex, String season,
                                     long points, int revealedCount, int hiddenCount,
                                     List<FixtureView> fixtures) {
    }

    public record MatchView(Long id, String competitionCode, TeamView homeTeam, TeamView awayTeam,
                            Instant kickoffUtc, String status, Integer homeScore, Integer awayScore) {

        public static MatchView from(Match match) {
            return new MatchView(match.getId(), match.getCompetition().getCode(),
                    TeamView.from(match.getHomeTeam()), TeamView.from(match.getAwayTeam()),
                    match.getKickoffUtc(), match.getStatus().name(), match.getHomeScore(), match.getAwayScore());
        }
    }
}
