package com.predictor.gameweek;

import com.predictor.catalog.Match;
import com.predictor.catalog.MatchRepository;
import com.predictor.gameweek.GameweekDtos.FixtureView;
import com.predictor.gameweek.GameweekDtos.GameweekView;
import com.predictor.prediction.Prediction;
import com.predictor.prediction.PredictionRepository;
import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;

    public GameweekService(GameweekRepository gameweeks, GameweekFixtureRepository fixtures,
                           MatchRepository matches, PredictionRepository predictions, Clock clock) {
        this.gameweeks = gameweeks;
        this.fixtures = fixtures;
        this.matches = matches;
        this.predictions = predictions;
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
