package com.tengames.league;

import com.tengames.TestcontainersConfiguration;
import com.tengames.catalog.CompetitionRepository;
import com.tengames.user.User;
import com.tengames.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A table for everyone who follows the same championship.
 *
 * <p>The choice is deliberately not derived from the favourite club: a
 * relegation would otherwise move a player, and their points, into a
 * different table overnight.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CompetitionLeagueIT {

    @Autowired
    private LeagueTableService tables;

    @Autowired
    private UserRepository users;

    @Autowired
    private CompetitionRepository competitions;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long premierLeague;
    private Long serieA;

    @BeforeEach
    void setUp() {
        premierLeague = competitions.findByCode("PL").orElseThrow().getId();
        serieA = competitions.findByCode("SA").orElseThrow().getId();
    }

    private User player(Long competitionId) {
        User user = users.save(new User("comp-%s@example.com".formatted(UUID.randomUUID()),
                passwordEncoder.encode("correct-horse"), "Player", "TN", null));
        user.setFavouriteCompetitionId(competitionId);
        return users.save(user);
    }

    @Test
    void theTableHoldsEveryoneWhoNamedThatChampionship() {
        User english = player(premierLeague);
        User alsoEnglish = player(premierLeague);
        User italian = player(serieA);

        LeagueTableService.ScopedTable table = tables.competitionTable(english.getId(), 0, 200);

        assertThat(table.available()).isTrue();
        assertThat(table.competitionName()).isEqualTo("Premier League");
        assertThat(table.table().entries()).extracting(LeagueTableService.Entry::userId)
                .contains(english.getPublicId(), alsoEnglish.getPublicId())
                .doesNotContain(italian.getPublicId());
    }

    @Test
    void aPlayerWhoChoseNothingHasNoTable() {
        // the card stays hidden, exactly as the country and club ones do
        User undecided = player(null);

        assertThat(tables.competitionTable(undecided.getId(), 0, 50).available()).isFalse();
    }

    @Test
    void theChoiceIsIndependentOfTheClub() {
        // an Arsenal supporter following Serie A is a normal state, and the
        // whole reason this is not derived from the club
        User user = player(serieA);
        user.setFavouriteClubTeamId(null);
        users.save(user);

        assertThat(tables.competitionTable(user.getId(), 0, 50).competitionName()).isEqualTo("Serie A");
    }
}
