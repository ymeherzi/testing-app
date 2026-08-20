package com.tengames.auth;

import com.tengames.common.web.RejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The last word on a password, and the only one that leaves the machine.
 *
 * <p>{@link PasswordPolicy} judges the string on its own: length, repetition,
 * runs along the keyboard, our own short list of the obvious. This asks the
 * wider question NIST SP 800-63B §5.1.1.2 actually cares about — has this
 * password already leaked? "chocolate1" passes every rule we wrote and has
 * been seen more than half a million times.
 *
 * <p>Run after the offline rules, because it is the slow one, and because a
 * password refused for being a keyboard run deserves to be told that rather
 * than "it has leaked".
 */
@Component
public class PasswordScreening {

    private final BreachedPasswords breached;
    private final boolean enabled;

    public PasswordScreening(BreachedPasswords breached,
                             @Value("${app.auth.check-breached-passwords:true}") boolean enabled) {
        this.breached = breached;
        this.enabled = enabled;
    }

    public void require(String password) {
        if (enabled && breached.check(password) == BreachedPasswords.Verdict.BREACHED) {
            throw new RejectedException("password.breached", "password",
                    "That password has appeared in known data breaches — it is in every attacker's "
                            + "dictionary by now. Pick another one");
        }
    }
}
