package com.tengames.catalog;

import java.text.Normalizer;
import java.util.Locale;

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

    private ClubNames() {
    }

    public static String key(String clubName) {
        String stripped = Normalizer.normalize(clubName == null ? "" : clubName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        stripped = stripped.replaceAll("\\b(fc|cf|ac|as|ss|sc|ssc|afc|rc|cd|ud|club|calcio|de|di|del|of)\\b", " ");
        return stripped.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
}
