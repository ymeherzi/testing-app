package com.tengames.push;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

/**
 * Posts an encrypted message to whatever push service the browser chose —
 * Google's, Apple's or Mozilla's; the endpoint decides, we never do.
 */
public class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    /** How long the service should hold the message for a device that is offline. */
    private static final int TTL_SECONDS = 12 * 60 * 60;

    private final RestClient restClient;
    private final WebPushCrypto crypto = new WebPushCrypto();
    private final VapidSigner vapid;
    private final ObjectMapper objectMapper;

    public WebPushSender(RestClient.Builder restClientBuilder, PushProperties properties,
                         ObjectMapper objectMapper, Clock clock) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.vapid = new VapidSigner(properties.publicKey(), properties.privateKey(), properties.subject(), clock);
    }

    @Override
    public void send(PushSubscription subscription, PushMessage message) {
        // the shape push-sw.js reads; anything else shows nothing
        byte[] body = crypto.encrypt(subscription.getP256dh(), subscription.getAuth(),
                objectMapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8));
        try {
            restClient.post()
                    .uri(subscription.getEndpoint())
                    .header("Authorization", vapid.authorizationFor(subscription.getEndpoint()))
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("TTL", String.valueOf(TTL_SECONDS))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // 404 and 410 are the push services' way of saying the device is
            // gone for good; anything else may work on the next attempt
            if (e.getStatusCode() == HttpStatus.NOT_FOUND || e.getStatusCode() == HttpStatus.GONE) {
                throw new PushSubscriptionGoneException(
                        "Push subscription %d is no longer valid".formatted(subscription.getId()));
            }
            log.warn("Push to subscription {} failed: {} {}", subscription.getId(), e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw e;
        }
    }

}
