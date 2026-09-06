package com.uxplima.uxmessentials.regions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Pure coverage of the flag-editor value logic: the {@link FlagState} cycle, its strict {@link FlagState#parse}
 * (which rejects an out-of-range value) and lenient {@link FlagState#of}, and the {@link FlagValue} round-trip the
 * editor and the port speak. No Bukkit, Paper, or WorldGuard: this is the domain's contract.
 */
class FlagStateTest {

    @Test
    void cyclesUnsetThenAllowThenDenyThenBackToUnset() {
        assertThat(FlagState.UNSET.next()).isEqualTo(FlagState.ALLOW);
        assertThat(FlagState.ALLOW.next()).isEqualTo(FlagState.DENY);
        assertThat(FlagState.DENY.next()).isEqualTo(FlagState.UNSET);
    }

    @Test
    void tokensAreTheWireFormThePortCarries() {
        assertThat(FlagState.ALLOW.token()).isEqualTo("ALLOW");
        assertThat(FlagState.DENY.token()).isEqualTo("DENY");
        assertThat(FlagState.UNSET.token()).isEmpty();
    }

    @Test
    void parsesKnownValuesCaseInsensitivelyWithBlankAsUnset() {
        assertThat(FlagState.parse("")).isEqualTo(FlagState.UNSET);
        assertThat(FlagState.parse("  ")).isEqualTo(FlagState.UNSET);
        assertThat(FlagState.parse("unset")).isEqualTo(FlagState.UNSET);
        assertThat(FlagState.parse("allow")).isEqualTo(FlagState.ALLOW);
        assertThat(FlagState.parse("ALLOW")).isEqualTo(FlagState.ALLOW);
        assertThat(FlagState.parse("Deny")).isEqualTo(FlagState.DENY);
    }

    @Test
    void strictParseRejectsAnInvalidValue() {
        assertThatThrownBy(() -> FlagState.parse("maybe")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lenientReadTreatsAnUnknownValueAsUnset() {
        assertThat(FlagState.of("maybe")).isEqualTo(FlagState.UNSET);
        assertThat(FlagState.of("greeting text")).isEqualTo(FlagState.UNSET);
        assertThat(FlagState.of("ALLOW")).isEqualTo(FlagState.ALLOW);
    }

    @Test
    void flagValueRoundTripsThroughAState() {
        FlagValue deny = FlagValue.of("pvp", FlagState.DENY);
        assertThat(deny.name()).isEqualTo("pvp");
        assertThat(deny.value()).isEqualTo("DENY");
        assertThat(deny.state()).isEqualTo(FlagState.DENY);

        FlagValue cleared = FlagValue.of("pvp", FlagState.UNSET);
        assertThat(cleared.value()).isEmpty();
        assertThat(cleared.state()).isEqualTo(FlagState.UNSET);
    }
}
