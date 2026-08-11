package com.tengames.push;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    /**
     * Without VAPID keys nothing can be delivered, but the app must still
     * start: local development and the test suite have no keys, and a missing
     * notification is not worth refusing to boot over.
     */
    @Bean
    public PushSender pushSender(PushProperties properties, RestClient.Builder restClientBuilder,
                                 ObjectMapper objectMapper, Clock clock) {
        if (properties.configured()) {
            return new WebPushSender(restClientBuilder, properties, objectMapper, clock);
        }
        log.warn("No VAPID keys configured — push notifications cannot be delivered "
                 + "(set VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY)");
        return new LoggingPushSender();
    }
}
