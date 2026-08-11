package com.tengames.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VAPID identity for this application server.
 *
 * @param publicKey  P-256 public key, base64url — also handed to the browser
 *                   when it subscribes
 * @param privateKey the matching 32-byte private scalar, base64url
 * @param subject    a mailto: or https: URL a push service can use to reach
 *                   the operator; required by RFC 8292
 */
@ConfigurationProperties("app.push")
public record PushProperties(String publicKey, String privateKey, String subject) {

    public PushProperties {
        if (subject == null || subject.isBlank()) {
            subject = "mailto:no-reply@tengames.app";
        }
    }

    boolean configured() {
        return isSet(publicKey) && isSet(privateKey);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
