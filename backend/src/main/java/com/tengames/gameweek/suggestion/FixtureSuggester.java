package com.tengames.gameweek.suggestion;

import com.tengames.catalog.Match;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the ten fixtures a gameweek should open with.
 *
 * <p>What we are after is a card that is genuinely hard to call: two big
 * clubs, or a derby where the table means little. A one-sided fixture is a
 * free three points for everybody and decides nothing.
 *
 * <p>The ranking is deliberately simple and explainable — every suggestion
 * comes back with the reason it was chosen, because an editor who cannot see
 * why will not trust the list. There is no odds feed and no strength rating
 * here; "close" is approximated by "both sides are heavyweights", which is
 * the honest limit of what we know before a ball is kicked.
 */
public final class FixtureSuggester {

    /** A derby outranks anything else: it is unpredictable by nature. */
    private static final int RIVALRY = 50;
    private static final int BOTH_ELITE = 30;
    private static final int ONE_ELITE = 10;

    /**
     * No competition may take more than this. Ten Premier League games would
     * technically score highest and make a dull, parochial card.
     */
    private static final int MAX_PER_COMPETITION = 3;

    public record Suggestion(Match match, int score, List<String> reasons) {
    }

    private FixtureSuggester() {
    }

    public static List<Suggestion> suggest(List<Match> pool, int size) {
        List<Suggestion> ranked = new ArrayList<>();
        for (Match match : pool) {
            ranked.add(rate(match));
        }
        ranked.sort(Comparator.comparingInt(Suggestion::score).reversed()
                .thenComparing(suggestion -> suggestion.match().getKickoffUtc()));

        List<Suggestion> picked = new ArrayList<>();
        Map<String, Integer> perCompetition = new HashMap<>();
        // First pass respects the variety cap; a second pass fills any gap so a
        // thin week still returns a full card rather than silently short-changing
        // the editor.
        for (Suggestion suggestion : ranked) {
            String code = suggestion.match().getCompetition().getCode();
            if (perCompetition.getOrDefault(code, 0) < MAX_PER_COMPETITION && picked.size() < size) {
                picked.add(suggestion);
                perCompetition.merge(code, 1, Integer::sum);
            }
        }
        for (Suggestion suggestion : ranked) {
            if (picked.size() >= size) {
                break;
            }
            if (!picked.contains(suggestion)) {
                picked.add(suggestion);
            }
        }
        return picked;
    }

    private static Suggestion rate(Match match) {
        String home = match.getHomeTeam().getName();
        String away = match.getAwayTeam().getName();
        List<String> reasons = new ArrayList<>();
        int score = BigMatches.competitionWeight(match.getCompetition().getCode());

        if (BigMatches.isRivalry(home, away)) {
            score += RIVALRY;
            reasons.add("derby");
        }
        boolean homeElite = BigMatches.isElite(home);
        boolean awayElite = BigMatches.isElite(away);
        if (homeElite && awayElite) {
            score += BOTH_ELITE;
            reasons.add("two big clubs");
        } else if (homeElite || awayElite) {
            score += ONE_ELITE;
            reasons.add("one big club");
        }
        if (reasons.isEmpty()) {
            reasons.add("fills the card");
        }
        return new Suggestion(match, score, List.copyOf(reasons));
    }
}
