package com.tengames.push;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Encryption written by hand is worth exactly what its tests prove.
 *
 * <p>The important one is the worked example from RFC 8291 §5: a round-trip
 * test only shows this code agrees with itself, and would happily pass with a
 * swapped HKDF salt or a missing record delimiter — mistakes that a browser
 * would reject and that would surface as "notifications silently never
 * arrive".
 */
class WebPushCryptoTest {

    // RFC 8291 §5, verbatim
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED_BODY =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml"
            + "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT"
            + "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    private final WebPushCrypto crypto = new WebPushCrypto();

    @Test
    void itMatchesTheWorkedExampleFromTheSpecification() {
        byte[] body = crypto.encrypt(UA_PUBLIC, AUTH_SECRET, PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.decodeBase64Url(SALT), WebPushCrypto.keyPairFrom(AS_PRIVATE, AS_PUBLIC));

        assertThat(WebPushCrypto.base64Url(body)).isEqualTo(EXPECTED_BODY);
    }

    @Test
    void aRealMessageCanBeReadBackByTheSubscriber() throws Exception {
        // the same path with a fresh salt and ephemeral key each time, decrypted
        // the way a browser would
        KeyPair subscriber = WebPushCrypto.generateKeyPair();
        String publicKey = WebPushCrypto.base64Url(
                WebPushCrypto.encodePoint((java.security.interfaces.ECPublicKey) subscriber.getPublic()));
        String authSecret = WebPushCrypto.base64Url(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        byte[] message = "Round 1 closes in an hour".getBytes(StandardCharsets.UTF_8);

        byte[] first = crypto.encrypt(publicKey, authSecret, message);
        byte[] second = crypto.encrypt(publicKey, authSecret, message);

        assertThat(decrypt(first, subscriber, authSecret, publicKey)).isEqualTo(message);
        // a repeated salt or ephemeral key would leak both plaintexts
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aKeyThatIsNotOnTheCurveIsRefused() {
        byte[] notAPoint = new byte[65];
        notAPoint[0] = 0x04;
        String encoded = WebPushCrypto.base64Url(notAPoint);

        // an attacker who can choose the point can otherwise recover the
        // private key (RFC 8291 §7). Which layer rejects it — the key factory
        // or the key agreement — is the JDK's business; that nothing is
        // encrypted with it is ours.
        assertThatThrownBy(() -> crypto.encrypt(encoded, AUTH_SECRET, new byte[]{1}))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theSubscriberKeyPairIsTheOneTheSpecificationDescribes() {
        // guards the raw-scalar parsing used for VAPID keys: a wrong length or a
        // sign byte would still produce a usable-looking key
        KeyPair pair = WebPushCrypto.keyPairFrom(UA_PRIVATE, UA_PUBLIC);

        assertThat(WebPushCrypto.base64Url(WebPushCrypto.encodePoint(
                (java.security.interfaces.ECPublicKey) pair.getPublic()))).isEqualTo(UA_PUBLIC);
    }

    /** What the browser does with the body, so the test proves it can be read. */
    private static byte[] decrypt(byte[] body, KeyPair subscriber, String authSecret, String uaPublic)
            throws Exception {
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        byte[] asPublic = Arrays.copyOfRange(body, 21, 86);
        byte[] ciphertext = Arrays.copyOfRange(body, 86, body.length);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(subscriber.getPrivate());
        agreement.doPhase(WebPushCrypto.publicKeyFrom(asPublic), true);
        byte[] shared = agreement.generateSecret();

        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.decodeBase64Url(uaPublic), asPublic);
        byte[] ikm = expand(hmac(WebPushCrypto.decodeBase64Url(authSecret), shared), keyInfo, 32);
        byte[] prk = hmac(salt, ikm);
        byte[] key = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.UTF_8), 16);
        byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.UTF_8), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).as("last-record delimiter").isEqualTo((byte) 2);
        return Arrays.copyOf(padded, padded.length - 1);
    }

    private static byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[]{1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
