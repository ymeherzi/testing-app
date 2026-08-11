package com.tengames.auth.email;

import java.util.Hashtable;
import javax.naming.NameNotFoundException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Asks DNS. An MX record is the proper answer; an A record is accepted too,
 * since a host with no MX still takes mail on itself by the old rule and
 * plenty of small domains rely on it.
 *
 * <p>Deliberately generous about failure: if the lookup times out or there is
 * no resolver, the verdict is UNKNOWN and the signup goes ahead. Nobody should
 * be locked out of the game because our DNS had a bad minute.
 */
@Component
public class DnsMailDomainLookup implements MailDomainLookup {

    private static final Logger log = LoggerFactory.getLogger(DnsMailDomainLookup.class);
    private static final String[] RECORDS = {"MX", "A", "AAAA"};

    private final Hashtable<String, String> environment = new Hashtable<>();

    public DnsMailDomainLookup() {
        environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        // a signup form is waiting on this: two seconds, one retry, then give up
        environment.put("com.sun.jndi.dns.timeout.initial", "2000");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");
    }

    @Override
    public Verdict check(String domain) {
        InitialDirContext context = null;
        try {
            context = new InitialDirContext(environment);
            Attributes found = context.getAttributes(domain, RECORDS);
            return found.size() > 0 ? Verdict.ACCEPTS : Verdict.MISSING;
        } catch (NameNotFoundException e) {
            return Verdict.MISSING;
        } catch (Exception e) {
            log.debug("Could not resolve mail domain {}: {}", domain, e.toString());
            return Verdict.UNKNOWN;
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ignored) {
                    // nothing useful to do with a failure to close a DNS context
                }
            }
        }
    }
}
