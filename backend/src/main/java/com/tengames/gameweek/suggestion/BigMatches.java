package com.tengames.gameweek.suggestion;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What makes a fixture worth putting in front of players: two heavyweights,
 * or a rivalry that means something regardless of the table.
 *
 * <p>This is editorial data, not computed truth. It is kept here, in one
 * readable place, rather than in the database: it changes about once a
 * season, and a wrong entry should be caught in review rather than fixed by
 * an UPDATE in production.
 *
 * <p>Club names arrive from the fixture provider and vary in spelling
 * ("Bayern München", "FC Bayern München"), so everything is matched on a
 * normalised key: lower case, accents stripped, common prefixes and suffixes
 * removed.
 */
final class BigMatches {

    /**
     * Clubs whose presence alone makes a fixture attractive.
     *
     * <p>Written as humans read them and normalised at startup, so an entry
     * like "Athletic Club" cannot quietly fail to match: the same key()
     * applies to the list and to the incoming name.
     */
    private static final Set<String> ELITE = normalise(
            // England
            "arsenal", "manchester city", "liverpool", "chelsea", "manchester united",
            "tottenham hotspur", "newcastle united", "aston villa",
            // Spain
            "real madrid", "barcelona", "atletico madrid", "athletic club", "real sociedad",
            "villarreal", "real betis", "sevilla",
            // Italy
            "inter", "milan", "juventus", "napoli", "roma", "lazio", "atalanta", "fiorentina",
            // Germany
            "bayern munchen", "borussia dortmund", "rb leipzig", "bayer leverkusen",
            "eintracht frankfurt", "vfb stuttgart",
            // France
            "paris saint germain", "marseille", "monaco", "lyon", "lille", "nice", "lens",
            // Portugal / Netherlands, for European nights
            "benfica", "porto", "sporting cp", "ajax", "psv", "feyenoord");

    private static Set<String> normalise(String... names) {
        Set<String> keys = new java.util.HashSet<>();
        for (String name : names) {
            keys.add(key(name));
        }
        return Set.copyOf(keys);
    }

    /**
     * Fixtures that sell themselves. Stored as an unordered pair joined by
     * "|", smaller key first, so home and away order never matters.
     */
    private static final Set<String> RIVALRIES = Set.of(
            pair("manchester united", "manchester city"),
            pair("liverpool", "everton"),
            pair("liverpool", "manchester united"),
            pair("arsenal", "tottenham hotspur"),
            pair("chelsea", "arsenal"),
            pair("real madrid", "barcelona"),
            pair("real madrid", "atletico madrid"),
            pair("sevilla", "real betis"),
            pair("athletic club", "real sociedad"),
            pair("inter", "milan"),
            pair("roma", "lazio"),
            pair("juventus", "inter"),
            pair("juventus", "torino"),
            pair("napoli", "juventus"),
            pair("bayern munchen", "borussia dortmund"),
            pair("borussia dortmund", "schalke 04"),
            pair("paris saint germain", "marseille"),
            pair("lyon", "saint etienne"),
            pair("benfica", "porto"),
            pair("benfica", "sporting cp"),
            pair("ajax", "feyenoord"));

    /**
     * How much each competition is worth on its own. The Champions League
     * tops it because a group-stage night still draws people who ignore a
     * mid-table league game.
     */
    private static final Map<String, Integer> COMPETITION_WEIGHT = Map.of(
            "CL", 10,
            "PL", 8,
            "PD", 8,
            "SA", 6,
            "BL1", 6,
            "FL1", 5);

    private BigMatches() {
    }

    static String key(String clubName) {
        String stripped = Normalizer.normalize(clubName == null ? "" : clubName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        // drop the corporate furniture and connectors that differ between
        // providers: "Atletico de Madrid" and "Atletico Madrid" are one club
        stripped = stripped.replaceAll("\\b(fc|cf|ac|as|ss|sc|ssc|afc|rc|cd|ud|club|calcio|de|di|del|of)\\b", " ");
        return stripped.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    static boolean isElite(String clubName) {
        return ELITE.contains(key(clubName));
    }

    static boolean isRivalry(String homeName, String awayName) {
        return RIVALRIES.contains(pair(key(homeName), key(awayName)));
    }

    static int competitionWeight(String competitionCode) {
        return COMPETITION_WEIGHT.getOrDefault(competitionCode, 3);
    }

    /** Unordered, normalised pair key, so home/away order never matters. */
    private static String pair(String a, String b) {
        String left = key(a);
        String right = key(b);
        return left.compareTo(right) <= 0 ? left + "|" + right : right + "|" + left;
    }
}
