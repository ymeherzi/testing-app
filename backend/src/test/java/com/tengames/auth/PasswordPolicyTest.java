package com.tengames.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "12341234" used to be an acceptable password: eight characters was the whole
 * rule. It is also one of the first few thousand strings any attacker tries.
 *
 * <p>The tests below are the passwords people actually choose, and the reason
 * each is refused — the reason matters, because the player sees it and has to
 * know what to change.
 */
class PasswordPolicyTest {

    private String reject(String password) {
        return reject(password, "player@example.com", "Alex");
    }

    private String reject(String password, String email, String name) {
        WeakPasswordException thrown = (WeakPasswordException) org.assertj.core.api.Assertions
                .catchThrowable(() -> PasswordPolicy.require(password, email, name));
        assertThat(thrown).as("expected %s to be refused", password).isNotNull();
        return thrown.getCode();
    }

    @Test
    void theOneThatStartedThis() {
        // eight characters no longer clears the bar at all
        assertThat(reject("12341234")).isEqualTo("password.tooShort");
        // and padding it out does not save it
        assertThat(reject("1234123412")).isEqualTo("password.repeated");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcabcabcabc", "aaaaaaaaaa", "abababababab", "1212121212"})
    void aFewCharactersOnRepeatAreRefused(String password) {
        assertThat(reject(password)).isEqualTo("password.repeated");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "abcdefghij", "azertyuiop", "qwertyuiop", "0987654321"})
    void arunAlongTheKeyboardIsRefused(String password) {
        // both layouts: the family this is for types on AZERTY
        assertThat(reject(password)).isIn("password.sequence", "password.common");
    }

    @Test
    void tooShortIsSaidPlainly() {
        assertThat(reject("Tr0ub4dor")).isEqualTo("password.tooShort");
        assertThat(PasswordPolicy.MIN_LENGTH).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"motdepasse", "Football2026", "liverpool!", "tengames!!"})
    void whatEverybodyElseChoseIsRefused(String password) {
        // the decorations people add are stripped before the list is consulted
        assertThat(reject(password)).isEqualTo("password.common");
    }

    @Test
    void aPasswordMadeOfTheOwnersOwnNameIsRefused() {
        assertThat(reject("alexandra-fan", "alexandra@example.com", "Alexandra"))
                .isEqualTo("password.personal");
        assertThat(reject("myname-is-alex", "someone@example.com", "Alex"))
                .isEqualTo("password.personal");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "correct horse battery staple",
            "j'ai vu le PSG gagner",
            "Vn7!kqmZ2rTx",
            "chaussette-violette-42",
    })
    void aPasswordSomebodyActuallyThoughtAboutIsAccepted(String password) {
        assertThatCode(() -> PasswordPolicy.require(password, "player@example.com", "Alex"))
                .doesNotThrowAnyException();
    }

    @Test
    void aMissingPasswordIsRefusedRatherThanCrashing() {
        assertThatThrownBy(() -> PasswordPolicy.require(null, "player@example.com", "Alex"))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void shortNamesAndAddressesAreNotUsedAsNeedles() {
        // "ana" appears in "banana"; refusing every password containing a
        // three-letter name would refuse far too much
        assertThatCode(() -> PasswordPolicy.require("banana-tarte-maison", "ana@example.com", "Ana"))
                .doesNotThrowAnyException();
    }
}
