package com.tengames.gameweek;

import com.tengames.gameweek.suggestion.BigMatches;
import java.util.Comparator;
import java.util.List;

/**
 * The order fixtures are shown in, in one place.
 *
 * <p>Kickoff decides it, as it always has. What this adds is the tie-break:
 * a Saturday three o'clock with three La Liga games and a Premier League one
 * used to come back in whatever order the database felt like, so the four
 * were interleaved. Fixtures that start at the very same minute are now kept
 * together by competition.
 *
 * <p>Only exactly simultaneous kickoffs are grouped. Pulling a 15:15 game up
 * next to the 15:00 one from its league would break the chronological reading
 * that the whole screen relies on.
 */
final class CardOrder {

    /**
     * Which competition leads when two start together. The ranking is
     * {@link BigMatches#competitionWeight} — the same one used to choose a
     * card in the first place, rather than a second table that would drift
     * away from it. Ties fall back to the code, then the fixture id, so the
     * order never changes between two loads of the same page.
     */
    private static final Comparator<GameweekFixture> COMPARATOR =
            Comparator.comparing((GameweekFixture fixture) -> fixture.getMatch().getKickoffUtc())
                    .thenComparing(fixture -> -BigMatches.competitionWeight(
                            fixture.getMatch().getCompetition().getCode()))
                    .thenComparing(fixture -> fixture.getMatch().getCompetition().getCode())
                    .thenComparing(GameweekFixture::getId);

    private CardOrder() {
    }

    static List<GameweekFixture> sorted(List<GameweekFixture> fixtures) {
        return fixtures.stream().sorted(COMPARATOR).toList();
    }
}
