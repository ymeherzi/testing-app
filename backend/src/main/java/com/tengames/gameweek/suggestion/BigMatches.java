package com.tengames.gameweek.suggestion;

import com.tengames.catalog.ClubNames;
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
public final class BigMatches {

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
            "benfica", "porto", "sporting cp", "ajax", "psv", "feyenoord",
            // Belgium / Scotland: only the two or three names anyone outside
            // the country would recognise. The rest of those leagues reaches a
            // card through a derby, or not at all.
            "anderlecht", "club brugge", "standard liege",
            "celtic", "rangers");

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
            pair("porto", "sporting cp"),
            pair("porto", "braga"),
            pair("ajax", "feyenoord"),
            pair("ajax", "psv"),
            pair("psv", "feyenoord"),
            // Belgium
            pair("anderlecht", "club brugge"),
            pair("anderlecht", "standard liege"),
            pair("club brugge", "cercle brugge"),
            pair("standard liege", "charleroi"),
            // Scotland
            pair("celtic", "rangers"),
            pair("heart of midlothian", "hibernian"),
            pair("dundee", "dundee united"),
            // England beyond the top flight, where most of these are played
            pair("west bromwich albion", "wolverhampton wanderers"),
            pair("cardiff city", "swansea city"),
            pair("bristol city", "cardiff city"),
            pair("portsmouth", "southampton"),
            pair("blackburn rovers", "burnley"),
            pair("millwall", "charlton athletic"),
            pair("derby county", "nottingham forest"),
            pair("norwich city", "ipswich town"),
            pair("sheffield united", "sheffield wednesday"),
            pair("birmingham city", "aston villa"),
            pair("middlesbrough", "sunderland"),
            pair("newcastle united", "sunderland"),
            pair("leeds united", "manchester united"));

    /**
     * How much each competition is worth on its own. The Champions League
     * tops it because a group-stage night still draws people who ignore a
     * mid-table league game.
     */
    private static final Map<String, Integer> COMPETITION_WEIGHT = Map.ofEntries(
            Map.entry("CL", 10),
            // the winners of the two European cups, once a year: as close to a
            // Champions League night as a single fixture gets
            Map.entry("USCUP", 10),
            // a one-off final between two champions is the definition of a
            // fixture worth putting in front of players
            Map.entry("CSHIELD", 9),
            Map.entry("TDC", 9),
            Map.entry("PL", 8),
            Map.entry("PD", 8),
            Map.entry("SA", 6),
            Map.entry("BL1", 6),
            Map.entry("FL1", 5),
            // The domestic cups. Worth less than a league weekend on their own,
            // because the early rounds are third-tier clubs — a tie that
            // matters gets there through the clubs playing it, not the badge.
            Map.entry("FACUP", 5),
            Map.entry("CDR", 5),
            Map.entry("COPPA", 5),
            Map.entry("DFB", 5),
            Map.entry("CDF", 5),
            Map.entry("EFLCUP", 4),
            // Leagues that reach a card through their own big matches:
            // Benfica-Porto and the Old Firm carry themselves, a midweek
            // fixture between two mid-table sides does not.
            Map.entry("PPL", 4),
            Map.entry("DED", 4),
            Map.entry("ELC", 3),
            Map.entry("BEL", 3),
            Map.entry("SCO", 3));

    private BigMatches() {
    }

    static String key(String clubName) {
        return ClubNames.key(clubName);
    }

    static boolean isElite(String clubName) {
        return ELITE.contains(key(clubName));
    }

    static boolean isRivalry(String homeName, String awayName) {
        return RIVALRIES.contains(pair(key(homeName), key(awayName)));
    }

    /**
     * Public because the display order of a card leans on the same judgement:
     * two fixtures kicking off at the same minute are shown biggest first.
     * One table, one opinion.
     */
    public static int competitionWeight(String competitionCode) {
        return COMPETITION_WEIGHT.getOrDefault(competitionCode, 3);
    }

    /** Unordered, normalised pair key, so home/away order never matters. */
    private static String pair(String a, String b) {
        String left = key(a);
        String right = key(b);
        return left.compareTo(right) <= 0 ? left + "|" + right : right + "|" + left;
    }
}
