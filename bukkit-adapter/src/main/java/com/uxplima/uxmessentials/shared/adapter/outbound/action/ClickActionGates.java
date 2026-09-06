package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates the four gate action types ({@code CHANCE}, {@code PERMISSION}, {@code CONDITION}, {@code COST})
 * for the {@link BukkitClickActionRunner} sequencer, returning a {@link Verdict} that tells the runner whether to
 * keep running the rest of the chain. Pulled out of the runner so the runner stays the thin sequencer and the
 * gate semantics (roll, node check, comparison, charge) live in one focused place.
 *
 * <p>Each gate's "denied" outcome stops the rest of the chain; a "passed" outcome continues. A malformed gate
 * spec is the deliberate exception: it is logged and treated as {@link Verdict#PASS} (the gate is skipped, never
 * the whole chain), so an operator typo degrades to "no gate" rather than silently swallowing every action after
 * it. {@code COST} is the one gate that also messages the viewer when it denies (insufficient funds), through the
 * {@code costDeniedKey} the owning context supplies, and the one whose charge runs through the optional
 * {@link ClickActionEconomy} seam: absent, the gate is skipped.
 */
@NullMarked
final class ClickActionGates {

    /** The whole-percent ceiling a {@code CHANCE} roll compares against. */
    private static final double FULL_PERCENT = 100.0;

    private final Permissions permissions;
    private final Optional<ClickActionEconomy> economy;
    private final CommandFeedback feedback;
    private final MessageKey costDeniedKey;
    private final Logger log;

    ClickActionGates(
            Permissions permissions,
            Optional<ClickActionEconomy> economy,
            CommandFeedback feedback,
            MessageKey costDeniedKey,
            Logger log) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.costDeniedKey = Objects.requireNonNull(costDeniedKey, "costDeniedKey");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Whether a gate verdict lets the chain keep running ({@link #PASS}) or stops it ({@link #DENY}). */
    enum Verdict {
        PASS,
        DENY
    }

    Verdict chance(String value) {
        Double percent = parsePercent(value);
        if (percent == null) {
            log.warn("event=click_action_bad_chance value={}", value);
            return Verdict.PASS; // a malformed gate is skipped, never aborting the chain
        }
        double roll = ThreadLocalRandom.current().nextDouble(FULL_PERCENT);
        return roll < percent ? Verdict.PASS : Verdict.DENY;
    }

    Verdict permission(Player viewer, String node) {
        String trimmed = node.strip();
        if (trimmed.isEmpty()) {
            log.warn("event=click_action_bad_permission value={}", node);
            return Verdict.PASS;
        }
        return permissions.has(BukkitRefs.toRef(viewer), trimmed) ? Verdict.PASS : Verdict.DENY;
    }

    Verdict condition(Player viewer, String value) {
        Optional<Comparison> parsed = Comparison.parse(value);
        if (parsed.isEmpty()) {
            log.warn("event=click_action_bad_condition value={}", value);
            return Verdict.PASS; // malformed: skip the gate, keep running
        }
        UnaryOperator<String> bridge = PlaceholderApiSupport.messageBridge(viewer.getUniqueId());
        Comparison comparison = parsed.get();
        boolean satisfied = comparison.evaluate(bridge.apply(comparison.left()), bridge.apply(comparison.right()));
        return satisfied ? Verdict.PASS : Verdict.DENY;
    }

    Verdict cost(Player viewer, String value) {
        BigDecimal amount = parseAmount(value);
        if (amount == null) {
            log.warn("event=click_action_bad_cost value={}", value);
            return Verdict.PASS;
        }
        if (economy.isEmpty()) {
            log.warn("event=click_action_cost_no_economy value={}", value);
            return Verdict.PASS; // no provider: the cost is ignored, the chain continues
        }
        if (economy.get().withdraw(BukkitRefs.toRef(viewer), amount, "default")) {
            return Verdict.PASS;
        }
        feedback.send(viewer, costDeniedKey, Map.of("amount", amount.toPlainString()));
        return Verdict.DENY;
    }

    private static @Nullable Double parsePercent(String value) {
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static @Nullable BigDecimal parseAmount(String value) {
        try {
            BigDecimal amount = new BigDecimal(value.strip());
            return amount.signum() < 0 ? null : amount;
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
