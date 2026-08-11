package com.tengames.gameweek;

import com.tengames.catalog.MatchRepository;
import com.tengames.gameweek.GameweekDtos.GameweekView;
import com.tengames.gameweek.GameweekDtos.MatchView;
import com.tengames.gameweek.suggestion.FixtureSuggester;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminGameweekController {

    private final GameweekService gameweekService;
    private final MatchRepository matches;
    private final Clock clock;

    public AdminGameweekController(GameweekService gameweekService, MatchRepository matches, Clock clock) {
        this.gameweekService = gameweekService;
        this.matches = matches;
        this.clock = clock;
    }

    /**
     * Gameweeks are identified by number; the weekend/midweek type is a
     * legacy scheduling hint that is no longer surfaced to players and may
     * be omitted (defaults to WEEKEND).
     */
    public record CreateGameweekRequest(
            @NotBlank String season,
            @Min(0) int weekIndex,
            Gameweek.Type type,
            @NotNull Instant windowStart,
            @NotNull Instant windowEnd,
            /** False for a warm-up round: played and scored, never ranked. */
            Boolean countsTowardsTable) {

        public CreateGameweekRequest {
            if (type == null) {
                type = Gameweek.Type.WEEKEND;
            }
            if (countsTowardsTable == null) {
                countsTowardsTable = true;
            }
        }
    }

    public record SetFixturesRequest(@NotEmpty List<Long> matchIds) {
    }

    /** A proposed fixture, with the reason it was picked so the editor can judge it. */
    public record SuggestionView(MatchView match, int score, List<String> reasons) {
    }

    public record ComposeRequest(
            @NotBlank String season,
            @Min(0) int weekIndex,
            Gameweek.Type type,
            @NotNull Instant windowStart,
            @NotNull Instant windowEnd,
            Boolean countsTowardsTable,
            Integer size) {

        public ComposeRequest {
            if (type == null) {
                type = Gameweek.Type.WEEKEND;
            }
            if (countsTowardsTable == null) {
                countsTowardsTable = true;
            }
            if (size == null || size < 1) {
                size = 10;
            }
        }
    }

    @GetMapping("/gameweeks")
    public List<GameweekView> listGameweeks() {
        return gameweekService.listAll();
    }

    @PostMapping("/gameweeks")
    public GameweekView create(@Valid @RequestBody CreateGameweekRequest request) {
        return gameweekService.createDraft(request.season(), request.weekIndex(), request.type(),
                request.windowStart(), request.windowEnd(), request.countsTowardsTable());
    }

    /**
     * Proposes a card for a window: ten fixtures by default, weighted towards
     * derbies and games between big clubs, spread across competitions. Only a
     * proposal — the editor adds, removes and reorders before saving.
     */
    @GetMapping("/gameweeks/suggestions")
    @Transactional(readOnly = true)
    public List<SuggestionView> suggestions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int size) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Instant fromInstant = (from != null ? from : today).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = (to != null ? to : today.plusDays(7)).plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        List<com.tengames.catalog.Match> pool =
                matches.findByKickoffUtcBetweenOrderByKickoffUtcAsc(fromInstant, toInstant);
        return FixtureSuggester.suggest(pool, size).stream()
                .map(s -> new SuggestionView(MatchView.from(s.match()), s.score(), s.reasons()))
                .toList();
    }

    /**
     * Creates a round and fills it with the proposed card in one call.
     *
     * <p>Building a gameweek by hand meant scrolling the whole calendar,
     * ticking boxes and picking a target from a dropdown — a lot of steps for
     * a weekly chore. The result is still a draft: the editor swaps whatever
     * they disagree with before publishing.
     */
    @PostMapping("/gameweeks/compose")
    @Transactional
    public GameweekView compose(@Valid @RequestBody ComposeRequest request) {
        List<com.tengames.catalog.Match> pool = matches.findByKickoffUtcBetweenOrderByKickoffUtcAsc(
                request.windowStart(), request.windowEnd());
        if (pool.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "No fixtures in that window — sync fixtures first");
        }
        // Season and week index are unique, and an empty draft from an earlier
        // attempt is the normal state to find here. Fill it rather than fail.
        GameweekView round = gameweekService.findDraft(request.season(), request.weekIndex())
                // an existing draft follows the window it is composed over,
                // otherwise it would hold fixtures outside its own dates
                .map(draft -> gameweekService.rescheduleDraft(draft.id(), request.windowStart(),
                        request.windowEnd(), request.countsTowardsTable()))
                .orElseGet(() -> gameweekService.createDraft(request.season(), request.weekIndex(),
                        request.type(), request.windowStart(), request.windowEnd(),
                        request.countsTowardsTable()));
        List<Long> matchIds = FixtureSuggester.suggest(pool, request.size()).stream()
                .map(s -> s.match().getId())
                .toList();
        return gameweekService.setFixtures(round.id(), matchIds);
    }

    @PutMapping("/gameweeks/{id}/fixtures")
    public GameweekView setFixtures(@PathVariable long id, @Valid @RequestBody SetFixturesRequest request) {
        return gameweekService.setFixtures(id, request.matchIds());
    }

    /**
     * Tops a round up with fixtures it was composed too early to include,
     * leaving the existing card — and everyone's predictions on it — alone.
     */
    @PostMapping("/gameweeks/{id}/fixtures")
    public GameweekView addFixtures(@PathVariable long id, @Valid @RequestBody SetFixturesRequest request) {
        return gameweekService.addFixtures(id, request.matchIds());
    }

    @PostMapping("/gameweeks/{id}/publish")
    public GameweekView publish(@PathVariable long id) {
        return gameweekService.publish(id);
    }

    /** Browse the synced match pool for curation. Defaults to the next 14 days. */
    @GetMapping("/matches")
    @Transactional(readOnly = true)
    public List<MatchView> matchPool(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String competition) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        Instant fromInstant = (from != null ? from : today.minusDays(2)).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = (to != null ? to : today.plusDays(14)).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<com.tengames.catalog.Match> pool = competition == null
                ? matches.findByKickoffUtcBetweenOrderByKickoffUtcAsc(fromInstant, toInstant)
                : matches.findByCompetitionCodeAndKickoffUtcBetweenOrderByKickoffUtcAsc(competition, fromInstant, toInstant);
        return pool.stream().map(MatchView::from).toList();
    }
}
