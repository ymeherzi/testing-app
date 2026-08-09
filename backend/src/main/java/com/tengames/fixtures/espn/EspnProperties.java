package com.tengames.fixtures.espn;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.espn")
public record EspnProperties(Boolean enabled, String baseUrl, List<String> leagues) {

    public EspnProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://site.api.espn.com/apis/site/v2/sports/soccer";
        }
        if (leagues == null || leagues.isEmpty()) {
            leagues = List.of("club.friendly", "usa.1", "arg.1", "mex.1", "bra.1");
        }
    }
}
