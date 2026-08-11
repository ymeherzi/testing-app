package com.tengames.push;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

/**
 * The {@code Authorization} header a push service demands: a JWT proving the
 * request comes from this application server (VAPID, RFC 8292).
 *
 * <p>The token is scoped to the push service's origin and expires, so it
 * cannot be replayed against another service or kept forever.
 */
final class VapidSigner {

    /** RFC 8292 §2 caps this at 24h; half that leaves room for clock skew. */
    private static final Duration TTL = Duration.ofHours(12);

    private final KeyPair keyPair;
    private final String subject;
    private final String publicKey;
    private final Clock clock;

    VapidSigner(String publicKey, String privateKey, String subject, Clock clock) {
        this.keyPair = WebPushCrypto.keyPairFrom(privateKey, publicKey);
        this.publicKey = publicKey;
        this.subject = subject;
        this.clock = clock;
    }

    String authorizationFor(String endpoint) {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        String header = base64Url("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        String payload = base64Url("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}"
                .formatted(audience, clock.instant().plus(TTL).getEpochSecond(), subject));
        String signingInput = header + "." + payload;
        String signature = WebPushCrypto.base64Url(sign(signingInput.getBytes(StandardCharsets.UTF_8)));
        return "vapid t=%s.%s, k=%s".formatted(signingInput, signature, publicKey);
    }

    private byte[] sign(byte[] signingInput) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(signingInput);
            return toRawSignature(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign the VAPID token", e);
        }
    }

    /**
     * Java signs into ASN.1 DER; JWS wants the two integers raw and padded to
     * 32 bytes each. Sending DER earns a 401 from every push service.
     */
    static byte[] toRawSignature(byte[] der) {
        int rLength = der[3];
        int sOffset = 4 + rLength + 1;
        int sLength = der[sOffset];
        BigInteger r = new BigInteger(Arrays.copyOfRange(der, 4, 4 + rLength));
        BigInteger s = new BigInteger(Arrays.copyOfRange(der, sOffset + 1, sOffset + 1 + sLength));
        byte[] raw = new byte[64];
        copyFixed(r, raw, 0);
        copyFixed(s, raw, 32);
        return raw;
    }

    private static void copyFixed(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, target, offset + 32 - length, length);
    }

    private static String base64Url(String json) {
        return WebPushCrypto.base64Url(json.getBytes(StandardCharsets.UTF_8));
    }
}
