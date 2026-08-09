package com.prono10.gameweek;

import com.prono10.catalog.Match;
import com.prono10.catalog.MatchRepository;
import com.prono10.gameweek.GameweekDtos.FixtureView;
import com.prono10.gameweek.GameweekDtos.GameweekSummary;
import com.prono10.gameweek.GameweekDtos.GameweekView;
import com.prono10.gameweek.GameweekDtos.PlayerGameweekView;
import com.prono10.prediction.Prediction;
import com.prono10.prediction.PredictionRepository;
import com.prono10.user.User;
import com.prono10.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GameweekService {

    private final GameweekRepository gameweeks;
    private final GameweekFixtureRepository fixtures;
    private final MatchRepository matches;
    private final PredictionRepository predictions;
    private final UserRepository users;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;
    private final Clock clock;

    public GameweekService(GameweekRepository gameweeks, GameweekFixtureRepository fixtures,
                           MatchRepository matches, PredictionRepository predictions,
                           UserRepository users, org.springframework.jdbc.core.simple.JdbcClient jdbc,
                           Clock clock) {
        this.gameweeks = gameweeks;
        this.fixtures = fixtures;
        this.matches = matches;
        this.predictions = predictions;
        this.users = users;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GameweekView currentForUser(long userId) {
        Gameweek gameweek = gameweeks
                .findFirstByStatusInOrderByWindowStartDesc(EnumSet.of(Gameweek.Status.PUBLISHED, Gameweek.Status.SCORED))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No published gameweek yet"));
        return viewForUser(gameweek, userId);
    }

    @Transactional(readOnly = true)
    public GameweekView byIdForUser(long gameweekId, long userId) {
        Gameweek gameweek = gameweeks.findById(gameweekId)
                .filter(gw -> gw.getStatus() != Gameweek.Status.DRAFT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gameweek not found"));
        return viewForUser(gameweek, userId);
    }

    /**
     * Every playable gameweek, newest first, with the caller's points —
     * the history list behind the gameweek switcher.
     */
    @Transactional(readOnly = true)
    public List<GameweekSummary> history(long userId) {
        return jdbc.sql("""
                        select gw.id, gw.season, gw.week_index, gw.type, gw.status,
                               gw.window_start, gw.window_end,
                               (select count(*) from gameweek_fixtures f where f.gameweek_id = gw.id) as fixture_count,
                               coalesce((select sum(p.points) from predictions p
                                         join gameweek_fixtures f2 on f2.id = p.gameweek_fixture_id
                                         where f2.gameweek_id = gw.id and p.user_id = :userId), 0) as my_points,
                               (select count(*) from predictions p2
                                join gameweek_fixtures f3 on f3.id = p2.gameweek_fixture_id
                                where f3.gameweek_id = gw.id and p2.user_id = :userId) as my_predictions
                        from gameweeks gw
                        where gw.status <> 'DRAFT'
                        order by gw.window_start desc
                        """)
                .param("userId", userId)
                .query((rs, i) -> new GameweekSummary(
                        rs.getLong("id"), rs.getString("season"), rs.getInt("week_index"),
                        rs.getString("type"), rs.getString("status"),
                        rs.getTimestamp("window_start").toInstant(), rs.getTimestamp("window_end").toInstant(),
                        rs.getInt("fixture_count"), rs.getLong("my_points"), rs.getInt("my_predictions")))
                .list();
    }

    /**
     * Another player's gameweek. Their scoreline is revealed only once the
     * match has locked (design §2.2 anti-copying rule) — before kickoff the
     * fixture is returned with a null prediction.
     */
    @Transactional(readOnly = true)
    public PlayerGameweekView playerView(long gameweekId, java.util.UUID publicPlayerId) {
        Gameweek gameweek = gameweeks.findById(gameweekId)
                .filter(gw -> gw.getStatus() != Gameweek.Status.DRAFT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gameweek not found"));
        User player = users.findByPublicId(publicPlayerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));
        long playerId = player.getId();
        Map<Long, Prediction> theirs = predictions
                .findByUserIdAndGameweekFixtureGameweekId(playerId, gameweekId).stream()
                .collect(Collectors.toMap(p -> p.getGameweekFixture().getId(), Function.identity()));
        Instant now = clock.instant();
        List<FixtureView> views = new ArrayList<>();
        long points = 0;
        int revealed = 0;
        int hidden = 0;
        for (GameweekFixture fixture : fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweekId)) {
            boolean locked = isLocked(fixture.getMatch(), now);
            Prediction prediction = theirs.get(fixture.getId());
            if (locked) {
                views.add(FixtureView.of(fixture, true, prediction));
                if (prediction != null) {
                    revealed++;
                    points += prediction.getPoints() == null ? 0 : prediction.getPoints();
                }
            } else {
                views.add(FixtureView.of(fixture, false, null));
                if (prediction != null) {
                    hidden++;
                }
            }
        }
        return new PlayerGameweekView(player.getPublicId(), player.getDisplayName(), player.getCountry(),
                gameweek.getId(), gameweek.getWeekIndex(), gameweek.getSeason(),
                points, revealed, hidden, views);
    }

    private GameweekView viewForUser(Gameweek gameweek, long userId) {
        List<GameweekFixture> gameweekFixtures = fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweek.getId());
        Map<Long, Prediction> mine = predictions.findByUserIdAndGameweekFixtureGameweekId(userId, gameweek.getId())
                .stream()
                .collect(Collectors.toMap(p -> p.getGameweekFixture().getId(), Function.identity()));
        Instant now = clock.instant();
        List<FixtureView> views = gameweekFixtures.stream()
                .map(fixture -> FixtureView.of(fixture, isLocked(fixture.getMatch(), now), mine.get(fixture.getId())))
                .toList();
        return GameweekView.of(gameweek, views);
    }

    /**
     * A prediction locks at the latest known kickoff (design §2.2). Matches
     * that have started, finished, or been voided are locked regardless of
     * clock skew in kickoff data.
     */
    public boolean isLocked(Match match, Instant now) {
        return !now.isBefore(match.getKickoffUtc())
                || switch (match.getStatus()) {
            case SCHEDULED, TIMED -> false;
            default -> true;
        };
    }

    // --- admin operations ---

    @Transactional
    public GameweekView createDraft(String season, int weekIndex, Gameweek.Type type,
                                    Instant windowStart, Instant windowEnd) {
        if (!windowEnd.isAfter(windowStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "windowEnd must be after windowStart");
        }
        Gameweek gameweek = gameweeks.save(new Gameweek(season, weekIndex, type, windowStart, windowEnd));
        return GameweekView.of(gameweek, List.of());
    }

    @Transactional
    public GameweekView setFixtures(long gameweekId, List<Long> matchIds) {
        Gameweek gameweek = requireGameweek(gameweekId);
        if (gameweek.getStatus() != Gameweek.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fixtures can only be edited on a DRAFT gameweek");
        }
        List<Match> selected = matches.findAllById(matchIds);
        if (selected.size() != matchIds.stream().distinct().count()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more match ids do not exist");
        }
        fixtures.deleteByGameweekId(gameweekId);
        fixtures.flush();
        fixtures.saveAll(selected.stream().map(match -> new GameweekFixture(gameweek, match)).toList());
        return viewForAdmin(gameweek);
    }

    @Transactional
    public GameweekView publish(long gameweekId) {
        Gameweek gameweek = requireGameweek(gameweekId);
        if (gameweek.getStatus() != Gameweek.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT gameweeks can be published");
        }
        if (fixtures.countByGameweekId(gameweekId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot publish a gameweek without fixtures");
        }
        gameweek.setStatus(Gameweek.Status.PUBLISHED);
        return viewForAdmin(gameweek);
    }

    @Transactional(readOnly = true)
    public List<GameweekView> listAll() {
        return gameweeks.findAll().stream()
                .sorted((a, b) -> b.getWindowStart().compareTo(a.getWindowStart()))
                .map(this::viewForAdmin)
                .toList();
    }

    private GameweekView viewForAdmin(Gameweek gameweek) {
        Instant now = clock.instant();
        List<FixtureView> views = fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweek.getId()).stream()
                .map(fixture -> FixtureView.of(fixture, isLocked(fixture.getMatch(), now), null))
                .toList();
        return GameweekView.of(gameweek, views);
    }

    private Gameweek requireGameweek(long gameweekId) {
        return gameweeks.findById(gameweekId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gameweek not found"));
    }
}
