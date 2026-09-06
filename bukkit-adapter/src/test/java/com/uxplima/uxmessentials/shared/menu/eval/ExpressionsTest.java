package com.uxplima.uxmessentials.shared.menu.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.ExpressionException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.Expressions;
import org.junit.jupiter.api.Test;

/**
 * Exercises the sandboxed expression evaluator end to end through its public facade. The grammar (precedence,
 * parentheses, power, functions, comparators, boolean logic) is covered alongside the failure modes a hostile or
 * fat-fingered menu config can throw at it, malformed input, division by zero, an identifier outside the
 * allow-list, and an oversized/over-nested expression. Each of which must surface as an {@link ExpressionException}
 * rather than a crash or a runaway evaluation.
 */
class ExpressionsTest {

    @Test
    void arithmeticPrecedenceAndParentheses() throws ExpressionException {
        assertThat(Expressions.evaluateNumber("2 + 3 * 4")).isEqualTo(14);
        assertThat(Expressions.evaluateNumber("(2 + 3) * 4")).isEqualTo(20);
        assertThat(Expressions.evaluateNumber("10 - 2 - 3")).isEqualTo(5);
        assertThat(Expressions.evaluateNumber("7 % 3")).isEqualTo(1);
        assertThat(Expressions.evaluateNumber("-5 + 2")).isEqualTo(-3);
        assertThat(Expressions.evaluateNumber("2.5 * 2")).isEqualTo(5);
    }

    @Test
    void powerIsRightAssociative() throws ExpressionException {
        assertThat(Expressions.evaluateNumber("2 ^ 3")).isEqualTo(8);
        assertThat(Expressions.evaluateNumber("2 ^ 3 ^ 2")).isEqualTo(512);
        assertThat(Expressions.evaluateNumber("-2 ^ 2")).isEqualTo(-4);
    }

    @Test
    void functionsAreAllowListed() throws ExpressionException {
        assertThat(Expressions.evaluateNumber("min(3, 1, 2)")).isEqualTo(1);
        assertThat(Expressions.evaluateNumber("max(3, 1, 2)")).isEqualTo(3);
        assertThat(Expressions.evaluateNumber("abs(-7)")).isEqualTo(7);
        assertThat(Expressions.evaluateNumber("floor(2.9)")).isEqualTo(2);
        assertThat(Expressions.evaluateNumber("ceil(2.1)")).isEqualTo(3);
        assertThat(Expressions.evaluateNumber("round(2.5)")).isEqualTo(3);
        assertThat(Expressions.evaluateNumber("sqrt(9)")).isEqualTo(3);
        assertThat(Expressions.evaluateNumber("max(1, min(8, 4))")).isEqualTo(4);
    }

    @Test
    void numericComparators() throws ExpressionException {
        assertThat(Expressions.evaluateBoolean("10 >= 5")).isTrue();
        assertThat(Expressions.evaluateBoolean("3 >= 5")).isFalse();
        assertThat(Expressions.evaluateBoolean("4 < 4")).isFalse();
        assertThat(Expressions.evaluateBoolean("4 <= 4")).isTrue();
        assertThat(Expressions.evaluateBoolean("2 == 2")).isTrue();
        assertThat(Expressions.evaluateBoolean("2 != 3")).isTrue();
        assertThat(Expressions.evaluateBoolean("1 + 1 > 1")).isTrue();
    }

    @Test
    void stringComparators() throws ExpressionException {
        assertThat(Expressions.evaluateBoolean("'world_nether' == 'world_nether'"))
                .isTrue();
        assertThat(Expressions.evaluateBoolean("\"a\" == \"b\"")).isFalse();
        assertThat(Expressions.evaluateBoolean("'a' != 'b'")).isTrue();
    }

    @Test
    void booleanLogic() throws ExpressionException {
        assertThat(Expressions.evaluateBoolean("true and false")).isFalse();
        assertThat(Expressions.evaluateBoolean("true or false")).isTrue();
        assertThat(Expressions.evaluateBoolean("not false")).isTrue();
        assertThat(Expressions.evaluateBoolean("1 < 2 and 3 < 4")).isTrue();
        assertThat(Expressions.evaluateBoolean("1 > 2 || 3 < 4")).isTrue();
        assertThat(Expressions.evaluateBoolean("!(1 == 1)")).isFalse();
    }

    @Test
    void powerOverflowAndModuloByZeroAreRejected() {
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateNumber("10 ^ 400"));
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateNumber("5 % 0"));
    }

    @Test
    void divisionByZeroIsRejected() {
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateNumber("1 / 0"));
    }

    @Test
    void malformedInputIsRejectedNotCrashed() {
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateNumber("2 +"));
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateNumber("(1 + 2"));
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateNumber("1 2 3"));
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateBoolean("5"));
    }

    @Test
    void unknownIdentifierOrFunctionIsRejected() {
        assertThatExceptionOfType(ExpressionException.class)
                .isThrownBy(() -> Expressions.evaluateNumber("system('rm')"));
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateNumber("foo + 1"));
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateNumber("sqrt(-1)"));
    }

    @Test
    void deeplyNestedAndOversizedInputAreBounded() {
        String deep = "(".repeat(500) + "1" + ")".repeat(500);
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateNumber(deep));
        String huge = "1" + "+1".repeat(5000);
        assertThatExceptionOfType(ExpressionException.class).isThrownBy(() -> Expressions.evaluateNumber(huge));
    }

    @Test
    void fractionalResultsRoundTrip() throws ExpressionException {
        assertThat(Expressions.evaluateNumber("1 / 3")).isCloseTo(0.3333333, within(1e-6));
        assertThat(Expressions.evaluateNumber("sqrt(2)")).isCloseTo(1.41421356, within(1e-6));
    }
}
