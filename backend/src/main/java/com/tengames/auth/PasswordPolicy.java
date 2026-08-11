package com.tengames.auth;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What counts as a password here.
 *
 * <p>Eight characters and nothing else let "12341234" through, which is the
 * kind of password that falls to a dictionary in well under a second.
 *
 * <p>The rules follow NIST SP 800-63B rather than the old habit of demanding a
 * capital, a digit and a symbol: composition rules push people towards
 * "Password1!" and no further. Length, and screening against what attackers
 * actually try, is what does the work. So: ten characters, no password built
 * from a handful of repeated or consecutive keys, nothing off the common list,
 * and nothing that is simply the player's own name or address.
 *
 * <p>Nothing is checked on the way in at login — an account created under the
 * old rule still signs in, and its owner can change the password when they
 * like.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    /** The shortest run of consecutive or adjacent keys we treat as a pattern. */
    private static final int RUN_LENGTH = 5;

    private static final Set<String> COMMON = Arrays.stream(("""
            password passwort passe motdepasse password1 passw0rd letmein welcome monkey dragon
            football footbal soccer baseball basketball superman batman starwars pokemon princess
            iloveyou trustno1 sunshine shadow master hello freedom whatever qazwsx michael
            jennifer jordan harley ranger hunter buster thomas robert charlie andrew daniel
            summer winter spring autumn january february chelsea arsenal liverpool barcelona
            realmadrid juventus milan inter bayern dortmund marseille paris psgpsg tengames
            manutd manchester united city ronaldo messi zidane neymar mbappe benzema
            admin administrator root user guest test testing demo default changeme
            secret access login signin computer internet samsung google apple android
            azerty qwerty qwertz motorola nintendo playstation xbox minecraft fortnite
            abc123 123abc a1b2c3 zaq12wsx 1qaz2wsx qweasdzxc asdfghjkl zxcvbnm
            """).split("\\s+")).collect(Collectors.toUnmodifiableSet());

    /** Rows and columns people slide a finger along, on both keyboard layouts. */
    private static final List<String> KEY_RUNS = List.of(
            "1234567890", "azertyuiop", "qwertyuiop", "qwertzuiop",
            "asdfghjklm", "qsdfghjklm", "zxcvbnm", "wxcvbn", "poiuytreza", "0987654321");

    private PasswordPolicy() {
    }

    /**
     * @throws WeakPasswordException with the reason, which the client turns
     *                               into its own wording
     */
    public static void require(String password, String email, String displayName) {
        String value = password == null ? "" : password;
        if (value.length() < MIN_LENGTH) {
            throw new WeakPasswordException("password.tooShort",
                    "Use at least %d characters".formatted(MIN_LENGTH));
        }
        if (isRepetitive(value)) {
            throw new WeakPasswordException("password.repeated",
                    "That is the same few characters over and over — mix it up");
        }
        if (hasLongRun(value)) {
            throw new WeakPasswordException("password.sequence",
                    "That is a straight run along the keyboard — try something less predictable");
        }
        if (isCommon(value)) {
            throw new WeakPasswordException("password.common",
                    "That password is on every attacker's list — pick something else");
        }
        if (containsPersonalDetail(value, email, displayName)) {
            throw new WeakPasswordException("password.personal",
                    "Leave your name and your email address out of your password");
        }
    }

    /**
     * "12341234", "abcabcabc", "aaaaaaaaaa" — a short unit, repeated. The
     * repetition need not come out even: "1234123412" is the same idea and the
     * same weakness, so the test is periodicity rather than exact division.
     */
    static boolean isRepetitive(String password) {
        if (password.chars().distinct().count() < 4) {
            return true;
        }
        for (int unit = 1; unit <= password.length() / 2; unit++) {
            if (isPeriodic(password, unit)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPeriodic(String password, int unit) {
        for (int i = unit; i < password.length(); i++) {
            if (password.charAt(i) != password.charAt(i % unit)) {
                return false;
            }
        }
        return true;
    }

    /** "12345678", "abcdefgh", "qwertyui" and the same backwards. */
    static boolean hasLongRun(String password) {
        String lower = password.toLowerCase(Locale.ROOT);
        int ascending = 1;
        int descending = 1;
        for (int i = 1; i < lower.length(); i++) {
            int step = lower.charAt(i) - lower.charAt(i - 1);
            ascending = step == 1 ? ascending + 1 : 1;
            descending = step == -1 ? descending + 1 : 1;
            if (ascending >= RUN_LENGTH || descending >= RUN_LENGTH) {
                return true;
            }
        }
        for (String row : KEY_RUNS) {
            for (int i = 0; i + RUN_LENGTH <= row.length(); i++) {
                if (lower.contains(row.substring(i, i + RUN_LENGTH))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The list itself, and what is left of the password once the decorations
     * everybody adds are stripped: "Football2026!" is "football".
     */
    static boolean isCommon(String password) {
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            return true;
        }
        String letters = lower.replaceAll("[^a-z]", "");
        return letters.length() >= 4 && COMMON.contains(letters);
    }

    static boolean containsPersonalDetail(String password, String email, String displayName) {
        String lower = password.toLowerCase(Locale.ROOT);
        String local = email == null ? "" : email.split("@")[0].toLowerCase(Locale.ROOT);
        return contains(lower, local) || contains(lower, displayName);
    }

    private static boolean contains(String password, String detail) {
        if (detail == null) {
            return false;
        }
        String needle = detail.toLowerCase(Locale.ROOT).trim();
        return needle.length() >= 4 && password.contains(needle);
    }
}
