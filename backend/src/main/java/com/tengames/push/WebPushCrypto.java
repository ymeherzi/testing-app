package com.tengames.push;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Web Push message encryption: RFC 8291 over the aes128gcm content encoding of
 * RFC 8188.
 *
 * <p>Written against the JDK rather than pulled from a library. The usual Java
 * dependency for this drags in Netty, jose4j and BouncyCastle 1.70 — an
 * artifact abandoned in 2021 with known advisories — to make one HTTPS POST.
 * The scheme itself is a hundred lines of well-specified key agreement.
 *
 * <p>Hand-written cryptography is only as trustworthy as its tests: this is
 * checked against the worked example in RFC 8291 §5, so an error that a
 * round-trip test would cancel out still fails the build.
 */
final class WebPushCrypto {

    private static final String CURVE = "secp256r1";
    /** Uncompressed point: 0x04 followed by X and Y. */
    private static final int POINT_LENGTH = 65;
    private static final int SALT_LENGTH = 16;
    private static final int KEY_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    /** Record size. One record is always enough: push payloads are ~4 KB at most. */
    private static final int RECORD_SIZE = 4096;

    private static final byte[] KEY_INFO_PREFIX = "WebPush: info\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.UTF_8);

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final SecureRandom random = new SecureRandom();

    /**
     * The encrypted body to POST, ready to send.
     *
     * @param userPublicKey  the browser's p256dh key, base64url
     * @param userAuthSecret the browser's auth secret, base64url
     */
    byte[] encrypt(String userPublicKey, String userAuthSecret, byte[] payload) {
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return encrypt(userPublicKey, userAuthSecret, payload, salt, generateKeyPair());
    }

    /**
     * The same, with the ephemeral key pair and salt supplied.
     *
     * <p>Only a test has any business calling this: reusing a salt or a key
     * pair across two messages leaks the plaintext of both.
     */
    byte[] encrypt(String userPublicKey, String userAuthSecret, byte[] payload, byte[] salt, KeyPair senderKeys) {
        try {
            byte[] uaPublic = B64D.decode(userPublicKey);
            byte[] authSecret = B64D.decode(userAuthSecret);
            byte[] asPublic = encodePoint((ECPublicKey) senderKeys.getPublic());

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(senderKeys.getPrivate());
            agreement.doPhase(publicKeyFrom(uaPublic), true);
            byte[] sharedSecret = agreement.generateSecret();

            // RFC 8291 §3.3: the auth secret salts the first extraction, and the
            // info binds the key to both parties' public keys, so a message
            // cannot be replayed at a different subscription.
            byte[] ikm = hkdf(authSecret, sharedSecret, concat(KEY_INFO_PREFIX, uaPublic, asPublic), 32);
            byte[] prk = hkdfExtract(salt, ikm);
            byte[] contentKey = hkdfExpand(prk, CEK_INFO, KEY_LENGTH);
            byte[] nonce = hkdfExpand(prk, NONCE_INFO, NONCE_LENGTH);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            // RFC 8188 §2: 0x02 marks the last record. Without the delimiter the
            // browser rejects the message rather than showing it.
            byte[] ciphertext = cipher.doFinal(concat(payload, new byte[]{2}));

            return ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + POINT_LENGTH + ciphertext.length)
                    .put(salt)
                    .putInt(RECORD_SIZE)
                    .put((byte) POINT_LENGTH)
                    .put(asPublic)
                    .put(ciphertext)
                    .array();
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt the push message", e);
        }
    }

    static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("P-256 unavailable", e);
        }
    }

    /** A key pair from a raw 32-byte private scalar, as VAPID keys are distributed. */
    static KeyPair keyPairFrom(String privateKeyBase64Url, String publicKeyBase64Url) {
        try {
            BigInteger d = new BigInteger(1, B64D.decode(privateKeyBase64Url));
            KeyFactory factory = KeyFactory.getInstance("EC");
            return new KeyPair(publicKeyFrom(B64D.decode(publicKeyBase64Url)),
                    factory.generatePrivate(new ECPrivateKeySpec(d, parameters())));
        } catch (Exception e) {
            throw new IllegalArgumentException("VAPID key pair is not a valid P-256 key", e);
        }
    }

    static ECPublicKey publicKeyFrom(byte[] uncompressedPoint) {
        if (uncompressedPoint.length != POINT_LENGTH || uncompressedPoint[0] != 0x04) {
            throw new IllegalArgumentException("Expected a 65-byte uncompressed P-256 point");
        }
        try {
            BigInteger x = new BigInteger(1, java.util.Arrays.copyOfRange(uncompressedPoint, 1, 33));
            BigInteger y = new BigInteger(1, java.util.Arrays.copyOfRange(uncompressedPoint, 33, POINT_LENGTH));
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), parameters()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a point on P-256", e);
        }
    }

    static byte[] encodePoint(ECPublicKey key) {
        byte[] encoded = new byte[POINT_LENGTH];
        encoded[0] = 0x04;
        copyFixed(key.getW().getAffineX(), encoded, 1);
        copyFixed(key.getW().getAffineY(), encoded, 33);
        return encoded;
    }

    static String base64Url(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    static byte[] decodeBase64Url(String value) {
        return B64D.decode(value);
    }

    private static ECParameterSpec parameters() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(CURVE));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    /**
     * A coordinate is always 32 bytes here; BigInteger drops leading zeroes and
     * adds a sign byte, either of which would shift the whole point.
     */
    private static void copyFixed(BigInteger coordinate, byte[] target, int offset) {
        byte[] bytes = coordinate.toByteArray();
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, target, offset + 32 - length, length);
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) {
        return hkdfExpand(hkdfExtract(salt, ikm), info, length);
    }

    private static byte[] hkdfExtract(byte[] salt, byte[] ikm) {
        return hmac(salt, ikm);
    }

    /** Only ever asked for 32 bytes or fewer, so one block of output is enough. */
    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) {
        byte[] block = hmac(prk, concat(info, new byte[]{1}));
        return java.util.Arrays.copyOf(block, length);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
