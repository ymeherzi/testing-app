package com.tengames.gameweek;

import com.tengames.TestcontainersConfiguration;
import com.tengames.auth.JwtService;
import com.tengames.catalog.Match;
import com.tengames.catalog.MatchRepository;
import com.tengames.fixtures.FixtureSyncService;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Topping a card up after the fact.
 *
 * <p>J0 was composed from what had been synced that evening and came out at
 * eight; the cups landed the next day. The editor needs to add the missing two
 * without recomposing, on a round that is already published — and without
 * disturbing the predictions already made. Predictions cascade from the fixture
 * row, so the rows that stay must be the same rows, not rebuilt copies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TopUpCardIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FixtureSyncService syncService;

    @Autowired
    private GameweekService gameweekService;

    @Autowired
    private MatchRepository matches;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String playerToken;
    private List<Match> pool;

    @BeforeEach
    void setUp() {
        syncService.syncAll();
        User admin = new User("topup-admin-%s@example.com".formatted(UUID.randomUUID()),
                passwordEncoder.encode("correct-horse"), "Admin", "TN", null);
        admin.setAdmin(true);
        adminToken = jwtService.issueToken(users.save(admin));
        playerToken = jwtService.issueToken(users.save(
                new User("topup-player-%s@example.com".formatted(UUID.randomUUID()),
                        passwordEncoder.encode("correct-horse"), "Player", "TN", null)));
        Instant now = Instant.now();
        // only fixtures still to come: a card can never gain a kicked-off match
        pool = matches.findByKickoffUtcBetweenOrderByKickoffUtcAsc(
                        now.plus(Duration.ofHours(2)), now.plus(Duration.ofDays(6))).stream()
                .toList();
        assertThat(pool.size()).isGreaterThan(3);
    }

    private long draftWith(String season, List<Long> matchIds) {
        long id = gameweekService.createDraft(season, 1, Gameweek.Type.WEEKEND,
                Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(6))).id();
        gameweekService.setFixtures(id, matchIds);
        return id;
    }

    private JsonNode addFixture(long gameweekId, long matchId, String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/admin/gameweeks/%d/fixtures".formatted(gameweekId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchIds\":[%d]}".formatted(matchId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long predict(long gameweekId, long fixtureId) throws Exception {
        mockMvc.perform(put("/api/gameweeks/%d/fixtures/%d/prediction".formatted(gameweekId, fixtureId))
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\":2,\"awayGoals\":1}"))
                .andExpect(status().isOk());
        return fixtureId;
    }

    /** The fixture ids on a round, as the player sees them. */
    private JsonNode playerView(long gameweekId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/gameweeks/" + gameweekId)
                        .header("Authorization", "Bearer " + playerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void aPublishedRoundCanStillGainAFixture() throws Exception {
        long id = draftWith("9970-01", pool.subList(0, 2).stream().map(Match::getId).toList());
        gameweekService.publish(id);

        JsonNode topped = addFixture(id, pool.get(2).getId(), adminToken);

        assertThat(topped.get("fixtures")).hasSize(3);
        assertThat(topped.get("status").asText()).isEqualTo("PUBLISHED");
    }

    @Test
    void addingAFixtureLeavesThePredictionsAlreadyMade() throws Exception {
        long id = draftWith("9970-02", pool.subList(0, 2).stream().map(Match::getId).toList());
        gameweekService.publish(id);
        JsonNode before = playerView(id);
        long predicted = predict(id, before.get("fixtures").get(0).get("fixtureId").asLong());

        addFixture(id, pool.get(2).getId(), adminToken);

        JsonNode after = playerView(id);
        JsonNode kept = after.get("fixtures").valueStream()
                .filter(f -> f.get("fixtureId").asLong() == predicted)
                .findFirst().orElseThrow();
        // the row survived, and so did what the player typed into it
        assertThat(kept.get("prediction").get("homeGoals").asInt()).isEqualTo(2);
        assertThat(kept.get("prediction").get("awayGoals").asInt()).isEqualTo(1);
    }

    @Test
    void swappingOneFixtureLeavesTheOtherRowsWhereTheyWere() throws Exception {
        List<Long> chosen = pool.subList(0, 3).stream().map(Match::getId).toList();
        long id = draftWith("9970-03", chosen);
        Map<Long, Long> before = rowsByMatch(id);

        // swap the last one for a fixture not on the card
        List<Long> swapped = List.of(chosen.get(0), chosen.get(1), pool.get(3).getId());
        gameweekService.setFixtures(id, swapped);

        Map<Long, Long> after = rowsByMatch(id);
        // rows that stayed keep their identity — anything hanging off them,
        // predictions above all, stays with them
        assertThat(after).containsEntry(chosen.get(0), before.get(chosen.get(0)))
                .containsEntry(chosen.get(1), before.get(chosen.get(1)))
                .containsKey(pool.get(3).getId())
                .doesNotContainKey(chosen.get(2));
    }

    private int removeFixture(long gameweekId, long fixtureId, String token) throws Exception {
        return mockMvc.perform(delete("/api/admin/gameweeks/%d/fixtures/%d".formatted(gameweekId, fixtureId))
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void aFixtureCanBeTakenOffAPublishedRound() throws Exception {
        List<Long> chosen = pool.subList(0, 3).stream().map(Match::getId).toList();
        long id = draftWith("9970-07", chosen);
        gameweekService.publish(id);
        Map<Long, Long> before = rowsByMatch(id);

        assertThat(removeFixture(id, before.get(chosen.get(2)), adminToken)).isEqualTo(200);

        Map<Long, Long> after = rowsByMatch(id);
        assertThat(after).hasSize(2).doesNotContainKey(chosen.get(2))
                // the rows that stayed are the same rows, predictions and all
                .containsEntry(chosen.get(0), before.get(chosen.get(0)))
                .containsEntry(chosen.get(1), before.get(chosen.get(1)));
    }

    @Test
    void thePredictionsOnTheOtherFixturesSurviveARemoval() throws Exception {
        long id = draftWith("9970-08", pool.subList(0, 3).stream().map(Match::getId).toList());
        gameweekService.publish(id);
        JsonNode before = playerView(id);
        long predicted = predict(id, before.get("fixtures").get(0).get("fixtureId").asLong());
        long doomed = before.get("fixtures").get(2).get("fixtureId").asLong();

        assertThat(removeFixture(id, doomed, adminToken)).isEqualTo(200);

        JsonNode kept = playerView(id).get("fixtures").valueStream()
                .filter(f -> f.get("fixtureId").asLong() == predicted)
                .findFirst().orElseThrow();
        assertThat(kept.get("prediction").get("homeGoals").asInt()).isEqualTo(2);
    }

    @Test
    void theLastFixtureCannotBeTakenAway() throws Exception {
        List<Long> chosen = pool.subList(0, 1).stream().map(Match::getId).toList();
        long id = draftWith("9970-09", chosen);
        gameweekService.publish(id);

        // a published round with no fixtures is a round nobody can play
        assertThat(removeFixture(id, rowsByMatch(id).get(chosen.getFirst()), adminToken)).isEqualTo(409);
    }

    @Test
    void aMatchThatHasBeenPlayedStaysOnTheCard() throws Exception {
        List<Long> chosen = pool.subList(0, 2).stream().map(Match::getId).toList();
        long id = draftWith("9970-10", chosen);
        Match played = matches.findById(chosen.getFirst()).orElseThrow();
        played.setScore(2, 1);
        played.setStatus(com.tengames.catalog.MatchStatus.FINISHED);
        matches.save(played);

        // its points are already in the table; taking it back would rewrite it
        assertThat(removeFixture(id, rowsByMatch(id).get(chosen.getFirst()), adminToken)).isEqualTo(409);
    }

    @Test
    void oneSlotCanBeSwappedOnAPublishedRound() throws Exception {
        List<Long> chosen = pool.subList(0, 2).stream().map(Match::getId).toList();
        long id = draftWith("9970-11", chosen);
        gameweekService.publish(id);
        Map<Long, Long> before = rowsByMatch(id);
        long replacement = pool.get(2).getId();

        mockMvc.perform(put("/api/admin/gameweeks/%d/fixtures/%d".formatted(id, before.get(chosen.get(1))))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchId\":%d}".formatted(replacement)))
                .andExpect(status().isOk());

        Map<Long, Long> after = rowsByMatch(id);
        assertThat(after).containsKey(replacement).doesNotContainKey(chosen.get(1))
                .containsEntry(chosen.get(0), before.get(chosen.get(0)));
    }

    @Test
    void removingIsAdminOnly() throws Exception {
        List<Long> chosen = pool.subList(0, 2).stream().map(Match::getId).toList();
        long id = draftWith("9970-12", chosen);

        assertThat(removeFixture(id, rowsByMatch(id).get(chosen.getFirst()), playerToken)).isEqualTo(403);
    }

    @Test
    void aFixtureFromAnotherRoundIsNotFound() throws Exception {
        long mine = draftWith("9970-13", pool.subList(0, 2).stream().map(Match::getId).toList());
        long other = draftWith("9970-14", pool.subList(2, 4).stream().map(Match::getId).toList());
        long strangerFixture = rowsByMatch(other).values().iterator().next();

        assertThat(removeFixture(mine, strangerFixture, adminToken)).isEqualTo(404);
    }

    @Test
    void aMatchAlreadyOnTheCardIsNotAddedTwice() throws Exception {
        List<Long> chosen = pool.subList(0, 2).stream().map(Match::getId).toList();
        long id = draftWith("9970-04", chosen);

        JsonNode topped = addFixture(id, chosen.get(0), adminToken);

        assertThat(topped.get("fixtures")).hasSize(2);
    }

    @Test
    void aKickedOffMatchCannotBeAdded() throws Exception {
        long id = draftWith("9970-05", pool.subList(0, 2).stream().map(Match::getId).toList());
        Match started = matches.findByKickoffUtcBetweenOrderByKickoffUtcAsc(
                Instant.now().minus(Duration.ofDays(3)), Instant.now().minus(Duration.ofMinutes(1))).getFirst();

        mockMvc.perform(post("/api/admin/gameweeks/%d/fixtures".formatted(id))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchIds\":[%d]}".formatted(started.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void toppingUpIsAdminOnly() throws Exception {
        long id = draftWith("9970-06", pool.subList(0, 2).stream().map(Match::getId).toList());

        mockMvc.perform(post("/api/admin/gameweeks/%d/fixtures".formatted(id))
                        .header("Authorization", "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matchIds\":[%d]}".formatted(pool.get(2).getId())))
                .andExpect(status().isForbidden());
    }

    /** Which row currently carries which match — the identity a prediction hangs off. */
    private Map<Long, Long> rowsByMatch(long gameweekId) {
        return gameweekService.listAll().stream()
                .filter(gw -> gw.id() == gameweekId)
                .findFirst().orElseThrow()
                .fixtures().stream()
                .collect(java.util.stream.Collectors.toMap(GameweekDtos.FixtureView::matchId,
                        GameweekDtos.FixtureView::fixtureId));
    }
}
