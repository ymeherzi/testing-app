package com.tengames.auth;

/**
 * Whether a password has already turned up in a breach.
 *
 * <p>Our own list of common passwords holds about a hundred entries and misses
 * everything else attackers actually try: "chocolate1" clears every rule we
 * have and has been seen more than half a million times. NIST SP 800-63B
 * §5.1.1.2 asks for a comparison against passwords known to be compromised,
 * and this is that comparison.
 */
public interface BreachedPasswords {

    enum Verdict {
        /** Known to have leaked. Whatever the count: once is enough to be in a wordlist. */
        BREACHED,
        /** Not in the corpus. */
        CLEAN,
        /** We could not tell — a timeout, a refusal, no network. */
        UNKNOWN
    }

    Verdict check(String password);
}
