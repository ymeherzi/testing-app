package com.tengames.catalog;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Reduces a club name to a comparable key, so the same club written two ways
 * by two sources is recognised as one.
 *
 * <p>"Arsenal FC" and "Arsenal" are the same club; "Atlético de Madrid" and
 * "Atletico Madrid" are the same club. What the key must never do is merge
 * clubs that merely share a word: Manchester City and Manchester United keep
 * distinct keys, which is exactly why the stripping is limited to legal-form
 * abbreviations and connectors rather than anything cleverer.
 *
 * <p>Lives here, next to the catalogue it identifies, because two callers
 * need it: the fixture importer deciding whether a club already exists, and
 * the editorial data behind fixture suggestions.
 */
public final class ClubNames {

    /**
     * Clubs whose two sources disagree by more than punctuation.
     *
     * <p>football-data returns registered names — "Sport Lisboa e Benfica",
     * "FC Internazionale Milano", "Olympique de Marseille" — while ESPN and
     * everyone else say Benfica, Inter, Marseille. Stripping cannot bridge
     * that, and the consequences were invisible: the Derby della Madonnina,
     * Le Classique, Ajax–Feyenoord and Benfica–Porto were all listed as
     * rivalries the app could never recognise, and the same club arrived
     * twice in the catalogue when a cup came from the other source.
     *
     * <p>Keys on both sides, canonical form on the right, and only for clubs
     * checked against what the two APIs actually return.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            // football-data's registered names
            entry("real sociedad futbol", "real sociedad"),
            entry("real betis balompie", "real betis"),
            entry("internazionale milano", "inter"),
            entry("atalanta bc", "atalanta"),
            entry("acf fiorentina", "fiorentina"),
            entry("bayer 04 leverkusen", "bayer leverkusen"),
            entry("olympique marseille", "marseille"),
            entry("olympique lyonnais", "lyon"),
            entry("lille osc", "lille"),
            entry("ogc nice", "nice"),
            entry("racing lens", "lens"),
            entry("sport lisboa e benfica", "benfica"),
            entry("sporting clube portugal", "sporting cp"),
            entry("feyenoord rotterdam", "feyenoord"),
            entry("sporting braga", "braga"),
            entry("sporting clube braga", "braga"),
            // ESPN's shorthand
            entry("ajax amsterdam", "ajax"),
            entry("psv eindhoven", "psv"),
            entry("bayern munich", "bayern munchen"),
            entry("internazionale", "inter"),
            entry("hamburg sv", "hamburger sv"),
            entry("standard liege", "standard"),
            entry("cercle brugge ksv", "cercle brugge"),
            entry("royal charleroi", "charleroi"),
            entry("union st gilloise", "union saint gilloise"),
            entry("heart midlothian", "hearts"));

    private ClubNames() {
    }

    public static String key(String clubName) {
        String stripped = Normalizer.normalize(clubName == null ? "" : clubName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        stripped = stripped.replaceAll("\\b(fc|cf|ac|as|ss|sc|ssc|afc|rc|cd|ud|club|calcio|de|di|del|of)\\b", " ");
        stripped = stripped.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return ALIASES.getOrDefault(stripped, stripped);
    }
}
