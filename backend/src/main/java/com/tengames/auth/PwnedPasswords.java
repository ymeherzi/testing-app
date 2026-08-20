package com.tengames.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Have I Been Pwned's range API, queried by k-anonymity.
 *
 * <p>The password never leaves this machine, and neither does enough of its
 * fingerprint to find it: we send the <em>first five characters</em> of the
 * SHA-1 and compare the remaining thirty-five against the few hundred suffixes
 * that come back. "P@ssw0rd" hashes to 21BD1|2DC18…; the prefix 21BD1 alone
 * matches nearly two thousand different passwords.
 *
 * <p>SHA-1 is not a choice here — it is the corpus's index, and it is being
 * used to look something up rather than to protect anything. Stored passwords
 * remain bcrypt.
 *
 * <p>Generous about failure, like the mail-domain lookup: any error is UNKNOWN
 * and the password goes through. Our network having a bad minute must not keep
 * anybody out of the game.
 */
@Component
public class PwnedPasswords implements BreachedPasswords {

    private static final Logger log = LoggerFactory.getLogger(PwnedPasswords.class);
    private static final int PREFIX_LENGTH = 5;

    private final RestClient restClient;

    public PwnedPasswords(RestClient pwnedRestClient) {
        this.restClient = pwnedRestClient;
    }

    @Override
    public Verdict check(String password) {
        if (password == null || password.isEmpty()) {
            return Verdict.CLEAN;
        }
        String hash = sha1(password);
        String prefix = hash.substring(0, PREFIX_LENGTH);
        String suffix = hash.substring(PREFIX_LENGTH);
        try {
            String body = restClient.get()
                    .uri("/range/{prefix}", prefix)
                    // padded responses hide the answer's size from anyone
                    // watching the wire; the filler entries carry a zero count
                    .header("Add-Padding", "true")
                    .retrieve()
                    .body(String.class);
            return body != null && seen(body, suffix) ? Verdict.BREACHED : Verdict.CLEAN;
        } catch (Exception e) {
            log.debug("Could not reach the breach corpus: {}", e.toString());
            return Verdict.UNKNOWN;
        }
    }

    /** Each line is SUFFIX:COUNT. A count of zero is padding, not a match. */
    private static boolean seen(String body, String suffix) {
        for (String line : body.split("\\R")) {
            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }
            if (line.substring(0, separator).trim().equalsIgnoreCase(suffix)
                    && !line.substring(separator + 1).trim().equals("0")) {
                return true;
            }
        }
        return false;
    }

    private static String sha1(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by every JVM", e);
        }
    }
}
