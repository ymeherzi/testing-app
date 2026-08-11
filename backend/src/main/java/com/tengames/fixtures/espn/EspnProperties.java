package com.tengames.fixtures.espn;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param leagues scoreboards polled for live scores. These must be the
 *                competitions our fixtures actually come from — the list
 *                once held leftovers from an early experiment (MLS,
 *                Argentina, friendlies), which meant no real match was ever
 *                followed live.
 * @param cups    competitions football-data's free tier does not carry,
 *                imported from ESPN instead. Each entry is
 *                {@code slug|CODE|Name}; the code is what the app shows and
 *                is limited to eight characters by the schema.
 */
@ConfigurationProperties("app.espn")
public record EspnProperties(Boolean enabled, String baseUrl, List<String> leagues, List<String> cups) {

    public EspnProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://site.api.espn.com/apis/site/v2/sports/soccer";
        }
        if (leagues == null || leagues.isEmpty()) {
            leagues = List.of("eng.1", "esp.1", "ita.1", "ger.1", "fra.1", "uefa.champions",
                    "eng.charity", "fra.super_cup");
        }
        if (cups == null || cups.isEmpty()) {
            cups = List.of(
                    "eng.charity|CSHIELD|FA Community Shield",
                    "fra.super_cup|TDC|Trophée des Champions");
        }
    }
}
