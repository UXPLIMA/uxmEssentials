package com.uxplima.uxmessentials.shared.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import org.junit.jupiter.api.Test;

class RefTest {

    @Test
    void parsesSimpleNamespacedRefWithNoArg() {
        Ref r = Ref.parse("warp:teleport");
        assertThat(r.id()).isEqualTo("warp:teleport");
        assertThat(r.value()).isEmpty();
    }

    @Test
    void parsesGenericRefWithArgAfterFirstColon() {
        Ref r = Ref.parse("sound:UI_BUTTON_CLICK");
        assertThat(r.id()).isEqualTo("sound");
        assertThat(r.value()).isEqualTo("UI_BUTTON_CLICK");
    }

    @Test
    void parsesBareIdAsNoArg() {
        assertThat(Ref.parse("close").id()).isEqualTo("close");
        assertThat(Ref.parse("close").value()).isEmpty();
    }

    @Test
    void rejectsBlankRaw() {
        assertThatThrownBy(() -> Ref.parse(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delegatingConstructorDefaultsToNoModifiers() {
        Ref parsed = Ref.parse("sound:click");
        assertThat(parsed.delayTicks()).isZero();
        assertThat(parsed.chance()).isEqualTo(100.0);
        assertThat(parsed.deny()).isEmpty();

        Ref built = Ref.of("give", Map.of());
        assertThat(built.delayTicks()).isZero();
        assertThat(built.chance()).isEqualTo(100.0);
        assertThat(built.deny()).isEmpty();
    }

    @Test
    void withModifiersCarriesDelayChanceAndDeny() {
        Ref deny = Ref.parse("message:no");
        Ref ref = Ref.parse("sound:LEVEL_UP").withModifiers(20, 25.0, deny);

        assertThat(ref.id()).isEqualTo("sound");
        assertThat(ref.value()).isEqualTo("LEVEL_UP");
        assertThat(ref.delayTicks()).isEqualTo(20);
        assertThat(ref.chance()).isEqualTo(25.0);
        assertThat(ref.deny()).contains(deny);
    }

    @Test
    void withModifiersClampsOutOfRangeValues() {
        Ref clamped = Ref.parse("give").withModifiers(-5, 250.0, null);
        assertThat(clamped.delayTicks()).as("a negative delay becomes now").isZero();
        assertThat(clamped.chance()).as("a chance above 100 caps at 100").isEqualTo(100.0);
        assertThat(clamped.deny()).isEmpty();

        assertThat(Ref.parse("give").withModifiers(0, -5.0, null).chance())
                .as("a negative chance floors at 0")
                .isZero();
    }

    @Test
    void withIdAndArgsReKeysButKeepsModifiers() {
        Ref deny = Ref.parse("message:no");
        // A registry-blind parse of a hyphenated generic leaves the whole token as the id (that is the bug the
        // runtime's registry-aware split fixes); the modifiers ride along on it.
        Ref whole = Ref.parse("give-money:100").withModifiers(20, 25.0, deny);
        assertThat(whole.id()).isEqualTo("give-money:100");

        Ref reKeyed = whole.withIdAndArgs("give-money", Map.of("value", "100"));

        assertThat(reKeyed.id()).isEqualTo("give-money");
        assertThat(reKeyed.value()).isEqualTo("100");
        assertThat(reKeyed.delayTicks()).as("the delay survives the re-key").isEqualTo(20);
        assertThat(reKeyed.chance()).as("the chance survives the re-key").isEqualTo(25.0);
        assertThat(reKeyed.deny()).as("the deny fallback survives the re-key").contains(deny);
    }

    @Test
    void parseSplitsAKnownGenericOnTheFirstColonOnly() {
        // message is a known generic prefix, so it splits, and only the first colon splits, so a value that
        // itself contains colons is preserved whole.
        Ref r = Ref.parse("message:Steve hi:there");
        assertThat(r.id()).isEqualTo("message");
        assertThat(r.value()).isEqualTo("Steve hi:there");
    }

    @Test
    void resolveLeavesARegisteredWholeTokenUnchanged() {
        // A feature ref whose whole id is registered (colon and all) resolves to itself, same identity, not re-split.
        Ref feature = Ref.parse("economy:open-bank");
        Ref resolved = feature.resolve(Set.of("economy:open-bank")::contains);
        assertThat(resolved).isSameAs(feature);
        assertThat(resolved.id()).isEqualTo("economy:open-bank");
        assertThat(resolved.value()).isEmpty();
    }

    @Test
    void resolveSplitsWhenOnlyTheHeadIsRegistered() {
        // has-money is registered but has-money:100 is not, so the head splits off and the tail becomes the value.
        Ref whole = Ref.parse("has-money:100");
        assertThat(whole.id()).as("the parser left it whole").isEqualTo("has-money:100");

        Ref resolved = whole.resolve(Set.of("has-money")::contains);

        assertThat(resolved.id()).isEqualTo("has-money");
        assertThat(resolved.value()).isEqualTo("100");
    }

    @Test
    void resolveSplitsOnTheFirstColonOnly() {
        Ref resolved = Ref.parse("has-money:50 coinsengine:gold").resolve(Set.of("has-money")::contains);
        assertThat(resolved.id()).isEqualTo("has-money");
        assertThat(resolved.value())
                .as("only the first colon splits, so a valued sub-spec survives")
                .isEqualTo("50 coinsengine:gold");
    }

    @Test
    void resolveLeavesATokenUnchangedWhenNeitherWholeNorHeadIsRegistered() {
        Ref unknown = Ref.parse("mystery:thing");
        Ref resolved = unknown.resolve(Set.of("has-money")::contains);
        assertThat(resolved)
                .as("neither the whole id nor its head resolves, so it misses just as before")
                .isSameAs(unknown);
    }

    @Test
    void resolveLeavesAColonFreeTokenUnchanged() {
        Ref bare = Ref.parse("has-prev");
        assertThat(bare.resolve(Set.<String>of()::contains)).isSameAs(bare);
    }

    @Test
    void resolveKeepsTheModifiersThroughTheSplit() {
        Ref deny = Ref.parse("message:no");
        Ref whole = Ref.parse("has-money:100").withModifiers(20, 25.0, deny);

        Ref resolved = whole.resolve(Set.of("has-money")::contains);

        assertThat(resolved.id()).isEqualTo("has-money");
        assertThat(resolved.value()).isEqualTo("100");
        assertThat(resolved.delayTicks()).isEqualTo(20);
        assertThat(resolved.chance()).isEqualTo(25.0);
        assertThat(resolved.deny()).contains(deny);
    }

    @Test
    void deniedAtDecidesTheChanceBoundaryFromAnInjectedRoll() {
        // Full chance never rolls a miss, whatever the draw.
        assertThat(Ref.parse("give").deniedAt(0.0)).isFalse();
        assertThat(Ref.parse("give").deniedAt(99.9)).isFalse();

        // Zero chance is always a miss: a [0,100) draw is always at or above 0.
        assertThat(Ref.parse("give").withModifiers(0, 0.0, null).deniedAt(0.0)).isTrue();

        // A 25% ref proceeds on a draw in [0,25) and misses at or above it.
        Ref quarter = Ref.parse("give").withModifiers(0, 25.0, null);
        assertThat(quarter.deniedAt(24.999)).isFalse();
        assertThat(quarter.deniedAt(25.0)).isTrue();
        assertThat(quarter.deniedAt(80.0)).isTrue();
    }
}
