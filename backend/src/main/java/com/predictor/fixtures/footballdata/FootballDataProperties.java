package com.predictor.fixtures.footballdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.footballdata")
public record FootballDataProperties(String baseUrl, String apiKey, Integer requestsPerMinute) {

    public FootballDataProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.football-data.org/v4";
        }
        if (requestsPerMinute == null || requestsPerMinute < 1) {
            // free tier allows 10/min; stay comfortably under it
            requestsPerMinute = 8;
        }
    }
}
