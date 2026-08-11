package com.tengames.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl    where the app answers, for the links inside emails — a
 *                   relative link is meaningless in an inbox
 * @param dailyBudget how many emails may leave in any rolling 24 hours. Resend's
 *                   free tier allows 100 a day and refuses the rest, so we stop
 *                   just short: a batch that stops early resumes on the next
 *                   tick, one that is refused loses the mail.
 */
@ConfigurationProperties("app.notifications")
public record NotificationProperties(String baseUrl, Integer dailyBudget) {

    public NotificationProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://tengames.app";
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (dailyBudget == null || dailyBudget <= 0) {
            dailyBudget = 90;
        }
    }
}
