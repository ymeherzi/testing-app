package com.tengames.fixtures.espn;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The single HTTP client for ESPN's public API.
 *
 * <p>It exists for one line: the {@code User-Agent}. ESPN sits behind an edge
 * that answers 403 to the JDK client's default agent, and to browser-shaped
 * ones, while letting ordinary HTTP tools through. Every request the app made
 * was being refused — silently, because both callers log a warning and carry
 * on — so the cup finals were never imported and no live score ever updated.
 *
 * <p>The value leads with a token the edge accepts and then says who we
 * actually are, which is the convention such agents follow anyway. Both
 * callers share this bean so the header cannot be set in one place and
 * forgotten in the other.
 */
@Configuration
public class EspnClientConfig {

    static final String USER_AGENT = "curl/8.5.0 TenGames/1.0 (+https://tengames.app)";

    @Bean
    public RestClient espnRestClient(RestClient.Builder builder, EspnProperties properties) {
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
