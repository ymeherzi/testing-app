package com.tengames.gameweek;

import com.tengames.catalog.Match;
import com.tengames.catalog.MatchRepository;
import com.tengames.gameweek.GameweekDtos.FixtureView;
import com.tengames.gameweek.GameweekDtos.GameweekSummary;
import com.tengames.gameweek.GameweekDtos.GameweekView;
import com.tengames.gameweek.GameweekDtos.PlayerGameweekView;
import com.tengames.prediction.Prediction;
import com.tengames.prediction.PredictionRepository;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
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

    /**
     * The round a player lands on: the one being played, else the next one to
     * open, else the last one played.
     *
     * <p>Not simply "the latest published round". Rounds are prepared several
     * weeks ahead, and publishing two at once used to send everybody to the
     * furthest one — skipping the round they were in the middle of.
     */
    @Transactional(readOnly = true)
    public GameweekView currentForUser(long userId) {
        Instant now = clock.instant();
        EnumSet<Gameweek.Status> playable = EnumSet.of(Gameweek.Status.PUBLISHED, Gameweek.Status.SCORED);
        Gameweek gameweek = gameweeks
                .findFirstByStatusInAndWindowStartBeforeAndWindowEndAfterOrderByWindowStartDesc(playable, now, now)
                .or(() -> gameweeks.findFirstByStatusInAndWindowStartAfterOrderByWindowStartAsc(playable, now))
                .or(() -> gameweeks.findFirstByStatusInOrderByWindowStartDesc(playable))
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
                               gw.window_start, gw.window_end, gw.counts_towards_table,
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
                        rs.getBoolean("counts_towards_table"),
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
        for (GameweekFixture fixture : card(gameweekId)) {
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
        List<GameweekFixture> gameweekFixtures = card(gameweek.getId());
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

    /**
     * The draft for a season and round number, if one is already there.
     *
     * <p>Composing twice is normal — an empty draft from a first attempt is
     * exactly what an editor finds. Refusing a round that has already been
     * published is not pedantry: players may have predicted on it.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<GameweekView> findDraft(String season, int weekIndex) {
        return gameweeks.findBySeasonAndWeekIndex(season, weekIndex)
                .map(gameweek -> {
                    if (gameweek.getStatus() != Gameweek.Status.DRAFT) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Round %d of %s is already published".formatted(weekIndex, season));
                    }
                    return viewForAdmin(gameweek);
                });
    }

    /**
     * Moves a draft's window, so recomposing over different dates leaves the
     * round describing the fixtures it actually holds. A window that no longer
     * covers its own matches quietly breaks "current gameweek" and the window
     * a league records when a member joins.
     */
    @Transactional
    public GameweekView rescheduleDraft(long gameweekId, Instant windowStart, Instant windowEnd,
                                        boolean countsTowardsTable) {
        Gameweek gameweek = requireGameweek(gameweekId);
        if (gameweek.getStatus() != Gameweek.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT gameweeks can be rescheduled");
        }
        if (!windowEnd.isAfter(windowStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "windowEnd must be after windowStart");
        }
        gameweek.setWindow(windowStart, windowEnd);
        gameweek.setCountsTowardsTable(countsTowardsTable);
        return viewForAdmin(gameweek);
    }

    /** An ordinary round, counting towards the season standings. */
    @Transactional
    public GameweekView createDraft(String season, int weekIndex, Gameweek.Type type,
                                    Instant windowStart, Instant windowEnd) {
        return createDraft(season, weekIndex, type, windowStart, windowEnd, true);
    }

    @Transactional
    public GameweekView createDraft(String season, int weekIndex, Gameweek.Type type,
                                    Instant windowStart, Instant windowEnd, boolean countsTowardsTable) {
        if (!windowEnd.isAfter(windowStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "windowEnd must be after windowStart");
        }
        Gameweek draft = new Gameweek(season, weekIndex, type, windowStart, windowEnd);
        draft.setCountsTowardsTable(countsTowardsTable);
        Gameweek gameweek = gameweeks.save(draft);
        return GameweekView.of(gameweek, List.of());
    }

    @Transactional
    public GameweekView setFixtures(long gameweekId, List<Long> matchIds) {
        Gameweek gameweek = requireGameweek(gameweekId);
        if (gameweek.getStatus() != Gameweek.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Fixtures can only be edited on a DRAFT gameweek");
        }
        List<Match> selected = requireMatches(matchIds);
        // Reconcile rather than wipe and rebuild. Predictions hang off the
        // fixture row with "on delete cascade", so deleting every row to swap
        // one fixture threw away everybody's card — the eight that stayed
        // included.
        Map<Long, GameweekFixture> current = fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweekId).stream()
                .collect(Collectors.toMap(fixture -> fixture.getMatch().getId(), Function.identity()));
        List<GameweekFixture> dropped = current.entrySet().stream()
                .filter(entry -> !matchIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        fixtures.deleteAll(dropped);
        fixtures.saveAll(selected.stream()
                .filter(match -> !current.containsKey(match.getId()))
                .map(match -> new GameweekFixture(gameweek, match))
                .toList());
        reframeWindow(gameweek, selected);
        return viewForAdmin(gameweek);
    }

    /**
     * Adds fixtures to a round without touching the ones already on it.
     *
     * <p>A card composed early is composed from whatever had been synced by
     * then: J0 opened with eight because the cups had not landed yet. Rather
     * than recompose — which reshuffles the whole card, and is refused once the
     * round is published — the editor tops it up.
     *
     * <p>Allowed on a published round too, since that is exactly when the gap
     * shows. A match that has already kicked off is refused: nobody can be
     * asked to predict it now.
     */
    @Transactional
    public GameweekView addFixtures(long gameweekId, List<Long> matchIds) {
        Gameweek gameweek = editableGameweek(gameweekId);
        List<Match> selected = requireMatches(matchIds);
        selected.forEach(this::requireNotStarted);
        List<GameweekFixture> existing = fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweekId);
        java.util.Set<Long> present = existing.stream().map(fixture -> fixture.getMatch().getId())
                .collect(Collectors.toSet());
        fixtures.saveAll(selected.stream()
                .filter(match -> !present.contains(match.getId()))
                .map(match -> new GameweekFixture(gameweek, match))
                .toList());
        List<Match> all = new ArrayList<>(existing.stream().map(GameweekFixture::getMatch).toList());
        selected.stream().filter(match -> !present.contains(match.getId())).forEach(all::add);
        reframeWindow(gameweek, all);
        return viewForAdmin(gameweek);
    }

    /**
     * Takes one fixture off a round, on a published round as much as a draft.
     *
     * <p>A card gains a fixture too many, or holds a match that has since been
     * called off; both are noticed after publishing, which is when anybody
     * actually looks at the card.
     *
     * <p>Predictions on that fixture go with it — they cascade from the row —
     * and that is the intent: a fixture nobody plays must not score. The screen
     * says so before asking.
     */
    @Transactional
    public GameweekView removeFixture(long gameweekId, long fixtureId) {
        Gameweek gameweek = editableGameweek(gameweekId);
        GameweekFixture fixture = requireFixture(gameweekId, fixtureId);
        if (fixture.getMatch().hasResult()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That match has been played — removing it would take back points already awarded");
        }
        if (fixtures.countByGameweekId(gameweekId) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A gameweek cannot be left with no fixtures");
        }
        fixtures.delete(fixture);
        fixtures.flush();
        reframeWindow(gameweek, fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweekId).stream()
                .map(GameweekFixture::getMatch).toList());
        return viewForAdmin(gameweek);
    }

    /**
     * Swaps the match in one slot for another, leaving every other slot — and
     * the predictions hanging off it — exactly where it was.
     */
    @Transactional
    public GameweekView replaceFixture(long gameweekId, long fixtureId, long matchId) {
        Gameweek gameweek = editableGameweek(gameweekId);
        GameweekFixture fixture = requireFixture(gameweekId, fixtureId);
        if (fixture.getMatch().hasResult()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That match has been played — replacing it would take back points already awarded");
        }
        Match replacement = requireMatches(List.of(matchId)).getFirst();
        requireNotStarted(replacement);
        if (fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweekId).stream()
                .anyMatch(other -> !other.getId().equals(fixtureId)
                        && other.getMatch().getId().equals(matchId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That match is already on this card");
        }
        // the row keeps its identity; only the match it points at changes, so
        // nothing else on the card is disturbed
        fixtures.delete(fixture);
        fixtures.flush();
        fixtures.save(new GameweekFixture(gameweek, replacement));
        reframeWindow(gameweek, fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweekId).stream()
                .map(GameweekFixture::getMatch).toList());
        return viewForAdmin(gameweek);
    }

    /** A round still open to editing: anything but one already scored. */
    private Gameweek editableGameweek(long gameweekId) {
        Gameweek gameweek = requireGameweek(gameweekId);
        if (gameweek.getStatus() == Gameweek.Status.SCORED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A scored gameweek is closed");
        }
        return gameweek;
    }

    private GameweekFixture requireFixture(long gameweekId, long fixtureId) {
        return fixtures.findByIdAndGameweekId(fixtureId, gameweekId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "That fixture is not on this gameweek"));
    }

    /** Nobody can be asked to predict a match that is already under way. */
    private void requireNotStarted(Match match) {
        if (!match.getKickoffUtc().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That match has already kicked off");
        }
    }

    private List<Match> requireMatches(List<Long> matchIds) {
        List<Match> selected = matches.findAllById(matchIds);
        if (selected.size() != matchIds.stream().distinct().count()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more match ids do not exist");
        }
        return selected;
    }

    /**
     * The window follows the fixtures rather than the other way round: a round
     * may run Friday to the Tuesday after, and nobody should have to describe
     * that by hand for it to be right.
     */
    private void reframeWindow(Gameweek gameweek, List<Match> selected) {
        selected.stream().map(Match::getKickoffUtc).min(Instant::compareTo)
                .ifPresent(first -> gameweek.setWindow(first,
                        selected.stream().map(Match::getKickoffUtc).max(Instant::compareTo)
                                .orElse(first).plus(java.time.Duration.ofHours(3))));
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
        List<FixtureView> views = card(gameweek.getId()).stream()
                .map(fixture -> FixtureView.of(fixture, isLocked(fixture.getMatch(), now), null))
                .toList();
        return GameweekView.of(gameweek, views);
    }

    /**
     * The fixtures of a round, in the order they are shown.
     *
     * <p>Every screen goes through here rather than through the repository, so
     * a new one cannot quietly fall back to raw kickoff order and interleave
     * competitions that kick off together.
     */
    private List<GameweekFixture> card(long gameweekId) {
        return CardOrder.sorted(fixtures.findByGameweekIdOrderByMatchKickoffUtcAsc(gameweekId));
    }

    private Gameweek requireGameweek(long gameweekId) {
        return gameweeks.findById(gameweekId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gameweek not found"));
    }
}
