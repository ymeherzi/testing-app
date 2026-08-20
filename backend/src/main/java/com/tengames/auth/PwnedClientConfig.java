package com.tengames.auth;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The client for the breach corpus, with a signup form waiting on it: two
 * seconds and we give up. Have I Been Pwned asks callers to identify
 * themselves, and does not want a key.
 */
@Configuration
public class PwnedClientConfig {

    static final String BASE_URL = "https://api.pwnedpasswords.com";
    static final String USER_AGENT = "TenGames/1.0 (+https://tengames.app)";

    private static SimpleClientHttpRequestFactory timeoutAfterTwoSeconds() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return factory;
    }

    @Bean
    public RestClient pwnedRestClient(RestClient.Builder builder) {
        return builder
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", USER_AGENT)
                .requestFactory(timeoutAfterTwoSeconds())
                .build();
    }
}
