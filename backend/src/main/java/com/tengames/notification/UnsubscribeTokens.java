package com.tengames.notification;

import com.tengames.auth.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * The proof carried by an unsubscribe link.
 *
 * <p>An HMAC of the player's public id rather than a stored token: there is
 * nothing to migrate, nothing to leak from the database, and a link cannot be
 * guessed from an id that is already public. The signing key is the one the
 * app already keeps secret.
 */
@Component
public class UnsubscribeTokens {

    private static final String ALGORITHM = "HmacSHA256";
    /** Half of the digest; still 128 bits, and short enough for a URL. */
    private static final int LENGTH = 32;

    private final SecretKeySpec key;

    public UnsubscribeTokens(JwtProperties properties) {
        this.key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String tokenFor(UUID publicId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(publicId.toString().getBytes(StandardCharsets.UTF_8)))
                    .substring(0, LENGTH);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Constant-time comparison: a token is a secret like any other. */
    public boolean isValid(UUID publicId, String token) {
        return token != null && MessageDigest.isEqual(
                tokenFor(publicId).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
