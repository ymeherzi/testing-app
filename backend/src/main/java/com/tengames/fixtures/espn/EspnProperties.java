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
 *                imported from ESPN instead: the domestic cups of the big
 *                five, the two super cups, and the Belgian and Scottish
 *                leagues, which are here for the Old Firm and the Topper
 *                rather than for their full calendars. Each entry is
 *                {@code slug|CODE|Name} — or {@code slug|CODE|Name|league} for
 *                the two that are leagues, which is what decides whether the
 *                competition can be somebody's favourite championship. The
 *                code is what the app shows and is limited to eight characters
 *                by the schema.
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
            leagues = List.of("eng.1", "eng.2", "esp.1", "ita.1", "ger.1", "fra.1",
                    "por.1", "ned.1", "bel.1", "sco.1",
                    "uefa.champions", "uefa.europa", "uefa.europa.conf", "uefa.super_cup",
                    "eng.charity", "fra.super_cup", "eng.league_cup", "eng.fa",
                    "esp.copa_del_rey", "ita.coppa_italia", "ger.dfb_pokal", "fra.coupe_de_france");
        }
        if (cups == null || cups.isEmpty()) {
            cups = List.of(
                    "uefa.super_cup|USCUP|UEFA Super Cup",
                    // the two European nights football-data's free tier does
                    // not carry either; the Champions League comes from there
                    // under the code CL
                    "uefa.europa|UEL|UEFA Europa League",
                    "uefa.europa.conf|UECL|UEFA Conference League",
                    "eng.charity|CSHIELD|FA Community Shield",
                    "fra.super_cup|TDC|Trophée des Champions",
                    // the domestic cups of the big five, none of which the
                    // free tier carries
                    "eng.fa|FACUP|FA Cup",
                    "eng.league_cup|EFLCUP|EFL Cup",
                    "esp.copa_del_rey|CDR|Copa del Rey",
                    "ita.coppa_italia|COPPA|Coppa Italia",
                    "ger.dfb_pokal|DFB|DFB-Pokal",
                    "fra.coupe_de_france|CDF|Coupe de France",
                    // and two leagues that exist for their own big matches
                    "bel.1|BEL|Jupiler Pro League|league",
                    "sco.1|SCO|Scottish Premiership|league");
        }
    }
}
