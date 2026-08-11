package com.tengames.auth.email;

import com.tengames.common.web.RejectedException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "bahla@asba.fr" was accepted: it is a well-formed address, so bean
 * validation had nothing to say. The domain does not exist, the six-digit code
 * went nowhere, and the player sat looking at "check your email".
 */
class EmailAddressesTest {

    private final List<String> asked = new ArrayList<>();

    private EmailAddresses with(MailDomainLookup.Verdict verdict) {
        return new EmailAddresses(domain -> {
            asked.add(domain);
            return verdict;
        }, true);
    }

    @Test
    void anAddressWhoseDomainDoesNotExistIsRefused() {
        assertThatThrownBy(() -> with(MailDomainLookup.Verdict.MISSING).require("bahla@asba.fr"))
                .isInstanceOf(RejectedException.class)
                .satisfies(e -> assertThat(((RejectedException) e).getCode()).isEqualTo("email.unreachable"));
        assertThat(asked).containsExactly("asba.fr");
    }

    @Test
    void anAddressThatCanReceiveMailGoesThrough() {
        assertThatCode(() -> with(MailDomainLookup.Verdict.ACCEPTS).require("alex@gmail.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void aResolverThatCannotAnswerNeverBlocksASignup() {
        // our DNS having a bad minute must not keep anybody out of the game
        assertThatCode(() -> with(MailDomainLookup.Verdict.UNKNOWN).require("alex@gmail.com"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alex@gmial.com", "alex@hotmial.com", "alex@yaho.com", "alex@outlok.com"})
    void theTyposOfTheBigProvidersAreCaughtWithoutAskingAnybody(String address) {
        // these resolve, some of them deliberately: the list is the only thing
        // that catches them
        assertThatThrownBy(() -> with(MailDomainLookup.Verdict.ACCEPTS).require(address))
                .isInstanceOf(RejectedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alex@localhost", "alex@", "alex@.com", "alex@example.", "no-at-sign"})
    void aDomainThatIsNotEvenADomainIsRefusedWithoutALookup(String address) {
        assertThatThrownBy(() -> with(MailDomainLookup.Verdict.ACCEPTS).require(address))
                .isInstanceOf(RejectedException.class);
        assertThat(asked).isEmpty();
    }

    @Test
    void theCheckCanBeTurnedOffWithoutLosingTheShapeRules() {
        EmailAddresses offline = new EmailAddresses(domain -> {
            asked.add(domain);
            return MailDomainLookup.Verdict.MISSING;
        }, false);

        assertThatCode(() -> offline.require("someone@asba.fr")).doesNotThrowAnyException();
        assertThatThrownBy(() -> offline.require("someone@localhost")).isInstanceOf(RejectedException.class);
        assertThat(asked).isEmpty();
    }
}
