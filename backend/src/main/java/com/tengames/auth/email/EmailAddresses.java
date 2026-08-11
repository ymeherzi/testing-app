package com.tengames.auth.email;

import com.tengames.common.web.RejectedException;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The address someone typed, checked as far as we honestly can before an
 * account exists.
 *
 * <p>Bean validation only judges the shape, and a typo keeps its shape:
 * "bahla@asba.fr" and "alex@gmial.com" are both well-formed and neither will
 * ever receive the code. Asking DNS whether the domain takes mail catches the
 * whole family of them.
 */
@Component
public class EmailAddresses {

    /** Typos for the addresses nearly everyone actually uses. */
    private static final Set<String> LOOKALIKES = Set.of(
            "gmial.com", "gmai.com", "gmail.co", "gmails.com", "gmail.con", "gnail.com",
            "hotmial.com", "hotmai.com", "hotmail.co", "hotmial.fr", "hotmil.com",
            "yaho.com", "yahou.com", "outlok.com", "outlook.co", "orang.fr", "wanadoo.f");

    private final MailDomainLookup lookup;
    private final boolean checkDomain;

    public EmailAddresses(MailDomainLookup lookup,
                          @Value("${app.auth.check-email-domain:true}") boolean checkDomain) {
        this.lookup = lookup;
        this.checkDomain = checkDomain;
    }

    /**
     * @throws RejectedException when the address cannot possibly receive the
     *                           code we are about to send it
     */
    public void require(String email) {
        String address = email == null ? "" : email.trim();
        int at = address.lastIndexOf('@');
        String domain = at < 0 ? "" : address.substring(at + 1).toLowerCase(Locale.ROOT);
        if (domain.isBlank() || !domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
            throw unreachable();
        }
        if (LOOKALIKES.contains(domain)) {
            throw unreachable();
        }
        if (checkDomain && lookup.check(domain) == MailDomainLookup.Verdict.MISSING) {
            throw unreachable();
        }
    }

    private RejectedException unreachable() {
        return new RejectedException("email.unreachable", "email",
                "That address can't receive mail — check the spelling");
    }
}
