package com.tengames.auth.email;

/**
 * Whether a domain can receive mail at all.
 *
 * <p>Shape is not enough: "bahla@asba.fr" is a perfectly well-formed address
 * whose domain does not exist, so the account was created, the code was sent
 * into the void, and the player waited for an email that was never coming.
 */
public interface MailDomainLookup {

    enum Verdict {
        /** The domain publishes somewhere to deliver mail. */
        ACCEPTS,
        /** The domain does not exist, or publishes nothing at all. */
        MISSING,
        /** We could not tell — a timeout, no resolver, a broken network. */
        UNKNOWN
    }

    Verdict check(String domain);
}
