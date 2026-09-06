package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.StringConditions;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Plain-JUnit coverage of the string condition pack. Each condition is pure string work (no Bukkit, no live player)
 * so these exercise the registered {@link BiPredicate} directly against a {@link MenuContext} and a placeholder
 * registry that resolves a single {@code %who%} token to a per-test value. Both truthy and falsy cases are pinned,
 * the operand-expansion is proven by comparing the resolved {@code %who%} text, and the fail-closed contract is
 * pinned by the malformed-argument cases (a bad regex, a non-integer bound, an unknown operator, a wrong token
 * count) all answering {@code false}.
 */
class StringConditionsTest {

    // --- contains ------------------------------------------------------------------------------------------------

    @Test
    void containsExpandsBothOperandsAndMatchesTheNeedle() {
        assertThat(evaluate("SteveTheKing", "contains", "%who% Steve")).isTrue();
        assertThat(evaluate("Notch", "contains", "%who% Steve")).isFalse();
    }

    @Test
    void containsFailsClosedWhenTheSecondOperandIsMissing() {
        assertThat(evaluate("SteveTheKing", "contains", "%who%")).isFalse();
    }

    // --- equals-ignorecase ---------------------------------------------------------------------------------------

    @Test
    void equalsIgnoreCaseIgnoresCase() {
        assertThat(evaluate("Steve", "equals-ignorecase", "%who% STEVE")).isTrue();
        assertThat(evaluate("Steve", "equals-ignorecase", "%who% Notch")).isFalse();
    }

    @Test
    void equalsIgnoreCaseKeepsSpacesInTheSecondOperand() {
        // Operand B is the whole remainder after the first whitespace, so its interior spaces are preserved.
        assertThat(evaluate("Steve The King", "equals-ignorecase", "%who% steve the king"))
                .isTrue();
    }

    // --- regex ---------------------------------------------------------------------------------------------------

    @Test
    void regexRequiresAFullMatch() {
        assertThat(evaluate("12345", "regex", "%who% \\d+")).isTrue();
        assertThat(evaluate("12a45", "regex", "%who% \\d+")).isFalse();
    }

    @Test
    void regexFailsClosedOnAMalformedPattern() {
        assertThat(evaluate("x", "regex", "%who% (")).isFalse();
    }

    // --- length --------------------------------------------------------------------------------------------------

    @Test
    void lengthComparesTheExpandedOperandLength() {
        assertThat(evaluate("SteveTheKing", "length", "%who% >= 3")).isTrue();
        assertThat(evaluate("SteveTheKing", "length", "%who% > 100")).isFalse();
        assertThat(evaluate("abc", "length", "%who% == 3")).isTrue();
        assertThat(evaluate("abc", "length", "%who% != 3")).isFalse();
    }

    @Test
    void lengthFailsClosedOnMalformedGrammar() {
        assertThat(evaluate("abc", "length", "%who% >= notanumber"))
                .as("non-integer bound")
                .isFalse();
        assertThat(evaluate("abc", "length", "%who% ?? 3"))
                .as("unknown operator")
                .isFalse();
        assertThat(evaluate("abc", "length", "%who% >="))
                .as("wrong token count")
                .isFalse();
    }

    // --- is-integer / is-double / is-object ----------------------------------------------------------------------

    @Test
    void isIntegerAcceptsOnlyWholeNumbers() {
        assertThat(evaluate("42", "is-integer", "%who%")).isTrue();
        assertThat(evaluate("4.5", "is-integer", "%who%")).isFalse();
        assertThat(evaluate("x", "is-integer", "%who%")).isFalse();
    }

    @Test
    void isDoubleAcceptsFiniteNumbersOnly() {
        assertThat(evaluate("4.5", "is-double", "%who%")).isTrue();
        assertThat(evaluate("x", "is-double", "%who%")).isFalse();
        assertThat(evaluate("Infinity", "is-double", "%who%")).isFalse();
        assertThat(evaluate("NaN", "is-double", "%who%")).isFalse();
    }

    @Test
    void isObjectIsTrueForAnyPresentValue() {
        assertThat(evaluate("hi", "is-object", "%who%")).isTrue();
        assertThat(evaluate("", "is-object", "%who%")).isFalse();
        assertThat(evaluate("   ", "is-object", "%who%")).isFalse();
    }

    // --- harness -------------------------------------------------------------------------------------------------

    /**
     * Fire condition {@code id} with {@code value} as the split-off argument, against a fresh binding set whose one
     * {@code %who%} placeholder resolves to {@code whoValue}. Building the bindings per call keeps each case isolated.
     */
    private static boolean evaluate(String whoValue, String id, String value) {
        MenuBindings bindings = new MenuBindings();
        bindings.placeholder("who", ctx -> whoValue);
        StringConditions.register(bindings, new NoopLogger());
        BiPredicate<MenuContext, Map<String, String>> condition =
                bindings.condition(id).orElseThrow(() -> new AssertionError("condition not registered: " + id));
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "Tester"), null, 0);
        return condition.test(ctx, Map.of("value", value));
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
