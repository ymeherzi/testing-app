package com.tengames.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Our own list of common passwords holds about a hundred entries. "chocolate1"
 * clears every rule we wrote and has been seen 567,912 times in breaches; so
 * have "elephant123" and "bonjour1234". This is the check that catches them.
 *
 * <p>The privacy of it rests on one thing, asserted below: only the first five
 * characters of the SHA-1 ever leave the machine. "P@ssw0rd" hashes to
 * 21BD1|2DC18…, and the prefix 21BD1 alone matches nearly two thousand
 * different passwords, so the answer says nothing about ours.
 */
class PwnedPasswordsTest {

    /** SHA-1 of "P@ssw0rd", split where the request splits it. */
    private static final String PREFIX = "21BD1";
    private static final String SUFFIX = "2DC183F740EE76F27B78EB39C8AD972A757";

    private MockRestServiceServer server;

    private PwnedPasswords lookup(String body, boolean fail) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.pwnedpasswords.com/range/" + PREFIX))
                .andRespond(fail
                        ? withServerError()
                        : withSuccess(body, MediaType.TEXT_PLAIN));
        return new PwnedPasswords(builder.baseUrl("https://api.pwnedpasswords.com").build());
    }

    @Test
    void onlyTheFirstFiveCharactersOfTheHashAreSent() {
        // the assertion is the URL itself: MockRestServiceServer fails the call
        // if anything but the prefix is asked for
        lookup(SUFFIX + ":6421042", false).check("P@ssw0rd");

        server.verify();
    }

    @Test
    void aPasswordInTheCorpusIsRefused() {
        assertThat(lookup("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" + SUFFIX + ":6421042", false)
                .check("P@ssw0rd")).isEqualTo(BreachedPasswords.Verdict.BREACHED);
    }

    @Test
    void aPasswordNobodyHasLeakedGoesThrough() {
        assertThat(lookup("0018A45C4D1DEF81644B54AB7F969B88D65:1", false).check("P@ssw0rd"))
                .isEqualTo(BreachedPasswords.Verdict.CLEAN);
    }

    @Test
    void paddingIsNotMistakenForAMatch() {
        // responses are padded with fake entries so the size gives nothing
        // away; those carry a count of zero
        assertThat(lookup(SUFFIX + ":0", false).check("P@ssw0rd"))
                .isEqualTo(BreachedPasswords.Verdict.CLEAN);
    }

    @Test
    void aServiceThatCannotAnswerNeverBlocksASignup() {
        assertThat(lookup("", true).check("P@ssw0rd")).isEqualTo(BreachedPasswords.Verdict.UNKNOWN);
    }
}
