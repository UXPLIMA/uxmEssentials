package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.kits.application.port.RequirementEvaluator;
import com.uxplima.uxmessentials.kits.domain.KitRequirement;
import com.uxplima.uxmessentials.kits.domain.RequirementOperator;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link RequirementEvaluator} backed by PlaceholderAPI. It resolves the {@code %placeholder%} tokens in a
 * requirement's two operands through the {@link PlaceholderApiSupport} seam, the one place the
 * {@code me.clip.placeholderapi} soft-depend is reached, then compares the resolved values. When both
 * resolved sides parse as numbers it compares them numerically as {@link BigDecimal} (so {@code 10} and
 * {@code 10.0} compare equal and the ordered operators {@code >= <= > <} are meaningful); otherwise only
 * {@link RequirementOperator#EQ}/{@link RequirementOperator#NEQ} apply as a string match and the ordered
 * operators fail closed. A side that resolves to blank (an unknown placeholder PlaceholderAPI leaves untouched
 * or empties) also fails closed, so the menu shows it as unmet (✘) and the gate denies the claim, matching the
 * fail-closed semantics the {@link RequirementEvaluator} port promises.
 *
 * <p>Threading: {@link PlaceholderAPI#setPlaceholders} is a main/region-thread operation. Every caller (the
 * claim gate on the recipient's owning thread, the menu renderer inside the viewer's entity-thread build) is
 * already on the correct thread; this class performs no scheduling of its own and must never be called off it.
 * It is registered only when PlaceholderAPI is present (see {@code KitsWiring}), so {@link #isPresent()} guards
 * its construction rather than each call.
 */
@NullMarked
public final class PapiRequirementEvaluator implements RequirementEvaluator {

    /** True when PlaceholderAPI is installed, so the wiring should register this evaluator. */
    public static boolean isPresent() {
        return PlaceholderApiSupport.isPresent();
    }

    @Override
    public boolean passes(PlayerRef who, KitRequirement requirement) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(requirement, "requirement");
        UnaryOperator<String> resolve = PlaceholderApiSupport.messageBridge(who.uuid());
        String left = resolve.apply(requirement.left()).strip();
        String right = resolve.apply(requirement.right()).strip();
        if (left.isBlank() || right.isBlank()) {
            return false;
        }
        return compare(left, requirement.operator(), right);
    }

    private boolean compare(String left, RequirementOperator op, String right) {
        Optional<BigDecimal> leftNum = number(left);
        Optional<BigDecimal> rightNum = number(right);
        if (leftNum.isPresent() && rightNum.isPresent()) {
            return compareNumeric(leftNum.get().compareTo(rightNum.get()), op);
        }
        // Non-numeric operands only support equality; the ordered comparisons fail closed.
        return switch (op) {
            case EQ -> left.equalsIgnoreCase(right);
            case NEQ -> !left.equalsIgnoreCase(right);
            case GTE, LTE, GT, LT -> false;
        };
    }

    private boolean compareNumeric(int cmp, RequirementOperator op) {
        return switch (op) {
            case EQ -> cmp == 0;
            case NEQ -> cmp != 0;
            case GTE -> cmp >= 0;
            case LTE -> cmp <= 0;
            case GT -> cmp > 0;
            case LT -> cmp < 0;
        };
    }

    private Optional<BigDecimal> number(String value) {
        try {
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
