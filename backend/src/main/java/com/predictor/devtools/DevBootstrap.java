package com.predictor.devtools;

import com.predictor.catalog.Match;
import com.predictor.catalog.MatchRepository;
import com.predictor.fixtures.FixtureSyncService;
import com.predictor.gameweek.Gameweek;
import com.predictor.gameweek.GameweekRepository;
import com.predictor.gameweek.GameweekService;
import com.predictor.league.LeagueRepository;
import com.predictor.league.LeagueService;
import com.predictor.user.User;
import com.predictor.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Makes a fresh dev environment immediately playable: seeded fixtures, a
 * known admin account (admin@dev.local / admin123!) and one published
 * demo gameweek containing every seed match state.
 */
@Component
@Profile("dev")
public class DevBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevBootstrap.class);

    public static final String ADMIN_EMAIL = "admin@dev.local";
    public static final String ADMIN_PASSWORD = "admin123!";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final FixtureSyncService syncService;
    private final GameweekRepository gameweeks;
    private final GameweekService gameweekService;
    private final MatchRepository matches;
    private final LeagueRepository leagues;
    private final LeagueService leagueService;
    private final Clock clock;

    public DevBootstrap(UserRepository users, PasswordEncoder passwordEncoder, FixtureSyncService syncService,
                        GameweekRepository gameweeks, GameweekService gameweekService,
                        MatchRepository matches, LeagueRepository leagues, LeagueService leagueService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.syncService = syncService;
        this.gameweeks = gameweeks;
        this.gameweekService = gameweekService;
        this.matches = matches;
        this.leagues = leagues;
        this.leagueService = leagueService;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmailIgnoreCase(ADMIN_EMAIL)) {
            log.info("Dev bootstrap: admin user already present, refreshing fixtures only");
        } else {
            User admin = new User(ADMIN_EMAIL, passwordEncoder.encode(ADMIN_PASSWORD), "Admin", null, null);
            admin.setAdmin(true);
            users.save(admin);
            log.info("Dev bootstrap: created admin user {} / {}", ADMIN_EMAIL, ADMIN_PASSWORD);
        }
        FixtureSyncService.SyncSummary summary = syncService.syncAll();
        log.info("Dev bootstrap: synced {} teams and {} matches", summary.teamsUpserted(), summary.matchesUpserted());
        if (gameweeks.count() == 0) {
            publishDemoGameweek();
        }
        seedDemoLeague();
    }

    private void seedDemoLeague() {
        if (leagues.count() > 0) {
            return;
        }
        User admin = users.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        var league = leagueService.create(admin.getId(), "Dev Demo League");
        User friend = users.findByEmailIgnoreCase("friend@dev.local").orElseGet(() -> {
            User created = new User("friend@dev.local", passwordEncoder.encode("friend123!"), "Friend", "FR", null);
            return users.save(created);
        });
        leagueService.join(friend.getId(), league.inviteCode());
        log.info("Dev bootstrap: created 'Dev Demo League' — invite code {}", league.inviteCode());
    }

    private void publishDemoGameweek() {
        Instant now = clock.instant();
        // The six seeded Premier League matches cover finished, live and upcoming.
        List<Long> matchIds = matches
                .findByCompetitionCodeAndKickoffUtcBetweenOrderByKickoffUtcAsc(
                        "PL", now.minus(Duration.ofDays(3)), now.plus(Duration.ofDays(5)))
                .stream()
                .map(Match::getId)
                .toList();
        if (matchIds.isEmpty()) {
            log.warn("Dev bootstrap: no seeded PL matches found, skipping demo gameweek");
            return;
        }
        Long gameweekId = gameweekService
                .createDraft(currentSeason(), 1, Gameweek.Type.WEEKEND,
                        now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(4)))
                .id();
        gameweekService.setFixtures(gameweekId, matchIds);
        gameweekService.publish(gameweekId);
        log.info("Dev bootstrap: published demo gameweek {} with {} fixtures", gameweekId, matchIds.size());
    }

    private String currentSeason() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        int startYear = today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
        return "%d-%02d".formatted(startYear, (startYear + 1) % 100);
    }
}
