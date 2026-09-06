package com.uxplima.uxmessentials.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The {@link VoterNameRules} validity contract: a well-formed name passes, while blank, the literal
 * {@code "null"}, over-length, and pattern-violating names are rejected. A blank pattern reduces to
 * length-only validation, and a malformed regex is tolerated. It compiles to no pattern so only the
 * length and blank/{@code "null"} checks apply. {@link java.util.regex.Pattern} has no value equality,
 * so behaviour is asserted through {@link VoterNameRules#isValid(String)} rather than record equality.
 */
class VoterNameRulesTest {

    @Test
    void aWellFormedNamePasses() {
        VoterNameRules rules = VoterNameRules.of(16, "");

        assertThat(rules.isValid("Notch")).isTrue();
    }

    @Test
    void aNullNameIsRejected() {
        assertThat(VoterNameRules.of(16, "").isValid(null)).isFalse();
    }

    @Test
    void aBlankNameIsRejected() {
        VoterNameRules rules = VoterNameRules.of(16, "");

        assertThat(rules.isValid("")).isFalse();
        assertThat(rules.isValid("   ")).isFalse();
    }

    @Test
    void theLiteralStringNullIsRejected() {
        assertThat(VoterNameRules.of(16, "").isValid("null")).isFalse();
    }

    @Test
    void anOverLengthNameIsRejected() {
        VoterNameRules rules = VoterNameRules.of(4, "");

        assertThat(rules.isValid("abcd")).isTrue();
        assertThat(rules.isValid("abcde")).isFalse();
    }

    @Test
    void aNameThatDoesNotMatchThePatternIsRejected() {
        VoterNameRules rules = VoterNameRules.of(16, "[A-Za-z0-9_]+");

        assertThat(rules.isValid("Valid_Name1")).isTrue();
        assertThat(rules.isValid("has space")).isFalse();
        assertThat(rules.isValid("dash-name")).isFalse();
    }

    @Test
    void aBlankPatternAcceptsAnyCharactersWithinLength() {
        VoterNameRules rules = VoterNameRules.of(16, "  ");

        assertThat(rules.isValid("any-thing!#")).isTrue();
    }

    @Test
    void aMalformedRegexFallsBackToLengthOnlyValidation() {
        // "[" is an unclosed character class: it must not throw, and only length/blank/"null" apply.
        VoterNameRules rules = VoterNameRules.of(5, "[");

        assertThat(rules.isValid("a!#")).isTrue();
        assertThat(rules.isValid("toolong")).isFalse();
        assertThat(rules.isValid("null")).isFalse();
        assertThat(rules.isValid("")).isFalse();
    }

    @Test
    void aMaxLengthBelowOneIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new VoterNameRules(0, java.util.Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
