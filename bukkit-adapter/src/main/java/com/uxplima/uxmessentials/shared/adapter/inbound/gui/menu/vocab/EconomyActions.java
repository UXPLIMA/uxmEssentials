package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.CurrencyProvider;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.PermissionQuery;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The economy slice of the menu action vocabulary: the ways a click (or an operator's {@code /menu} spec) can move
 * money, experience or a permission node onto the viewer without a feature wiring it. Registered once at startup
 * into the shared {@link MenuBindings} alongside {@link MenuVocabulary}, {@link MessagingActions},
 * {@link CommandActions} and {@link MovementActions}, so a disk-loaded spec resolves {@code give-money} /
 * {@code take-exp} / {@code give-permission} the same way a code-registered feature menu does. This is the first
 * consumer of the Phase-0 {@link Currencies} multi-currency façade and the Vault {@link PermissionQuery} seam.
 *
 * <p>Every effect here is <em>silent</em> by design: none of them tells the player anything. That mirrors
 * DeluxeMenus: an operator who wants feedback pairs the economy action with a {@code [message]} action of their own.
 * So no {@code MessageKey} is involved; the amounts, currency specs and permission nodes are operator config, and
 * the only text any action produces is a diagnostic line on the operator {@code log} when an argument is malformed.
 *
 * <p>The money actions route through {@link Currencies#resolve(String)}: a blank currency spec falls back to the
 * configured default currency, a named one ({@code playerpoints}, {@code coinsengine:gold}, …) reaches that
 * back-end, and an unavailable back-end already no-ops rather than throwing. The experience and level actions use
 * Paper's native {@code giveExp} / {@code giveExpLevels}; a {@code take} never drives a player negative (experience
 * is bounded by the player's current total, a level is clamped at zero). The permission actions go through the
 * Vault {@link PermissionQuery}; when Vault (or its permission service) is absent the query is
 * {@link PermissionQuery#ABSENT} and {@code add}/{@code remove} are graceful no-ops, never an error.
 *
 * <p>Every action is fail-soft: a malformed amount is a logged no-op, and anything a call unexpectedly throws is
 * turned into a logged no-op by {@link #safe} rather than escaping back into the click dispatch, one bad effect
 * must not abort the rest of a chain. All of it runs on the viewer's entity thread, where a click already runs and
 * where the currency and permission capabilities document they must be called.
 */
public final class EconomyActions {

    private EconomyActions() {}

    /**
     * Register the economy actions into {@code bindings}. {@code currencies} is the Phase-0 multi-currency façade
     * the money actions resolve a back-end through; {@code permissions} is the Vault permission seam the permission
     * actions grant/revoke through (its {@link PermissionQuery#ABSENT} default makes those graceful no-ops when
     * Vault is absent); {@code log} is the operator console logger a fail-soft or malformed action warns through.
     * Left separate from {@link MenuVocabulary#registerActions} so that method's existing call-sites stay untouched
     *: the composition root calls both.
     */
    public static void register(MenuBindings bindings, Currencies currencies, PermissionQuery permissions, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(currencies, "currencies");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(log, "log");
        registerMoney(bindings, currencies, log);
        registerExperience(bindings, log);
        registerPermission(bindings, permissions, log);
    }

    /** {@code give/take/set-money} (currency-spec aware) plus the {@code playerpoints}-pinned points convenience actions. */
    private static void registerMoney(MenuBindings bindings, Currencies currencies, Logger log) {
        alias(bindings, "give-money", "givemoney", safe("give-money", log, ctx -> giveMoney(ctx, currencies, log)));
        alias(bindings, "take-money", "takemoney", safe("take-money", log, ctx -> takeMoney(ctx, currencies, log)));
        alias(bindings, "set-money", "setmoney", safe("set-money", log, ctx -> setMoney(ctx, currencies, log)));
        bindings.action("give-points", safe("give-points", log, ctx -> givePoints(ctx, currencies, log)));
        bindings.action("take-points", safe("take-points", log, ctx -> takePoints(ctx, currencies, log)));
    }

    /** {@code give/take-exp} (experience points) and {@code give/take-levels} (whole levels), with their aliases. */
    private static void registerExperience(MenuBindings bindings, Logger log) {
        alias(bindings, "give-exp", "give-xp", safe("give-exp", log, ctx -> giveExp(ctx, log)));
        alias(bindings, "take-exp", "take-xp", safe("take-exp", log, ctx -> takeExp(ctx, log)));
        alias(bindings, "give-levels", "give-level", safe("give-levels", log, ctx -> giveLevels(ctx, log)));
        alias(bindings, "take-levels", "take-level", safe("take-levels", log, ctx -> takeLevels(ctx, log)));
    }

    /** {@code give/take-permission} over the Vault permission seam (graceful no-ops when Vault is absent). */
    private static void registerPermission(MenuBindings bindings, PermissionQuery permissions, Logger log) {
        alias(
                bindings,
                "give-permission",
                "give-perm",
                safe("give-permission", log, ctx -> givePerm(ctx, permissions)));
        alias(
                bindings,
                "take-permission",
                "take-perm",
                safe("take-permission", log, ctx -> takePerm(ctx, permissions)));
    }

    /** Bind {@code handler} under a canonical id and one short alias, so an operator may write either. */
    private static void alias(MenuBindings bindings, String id, String alias, Consumer<MenuActionContext> handler) {
        bindings.action(id, handler);
        bindings.action(alias, handler);
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a no-op rather
     * than escaping into the click dispatch. The same fail-soft contract the sibling action packs and the npc/
     * holograms click runner apply to their effect actions.
     */
    private static Consumer<MenuActionContext> safe(String action, Logger log, Consumer<MenuActionContext> body) {
        return ctx -> {
            try {
                body.accept(ctx);
            } catch (RuntimeException failure) {
                log.warn("event=menu_economy_action_failed action={} value={}", action, ctx.arg());
            }
        };
    }

    // --- money -------------------------------------------------------------------------------------------------------

    /** Deposit {@code <amount>} of {@code [currency-spec]} (blank → default) onto the viewer; a bad amount warns+skips. */
    private static void giveMoney(MenuActionContext ctx, Currencies currencies, Logger log) {
        money(ctx, "give-money", log)
                .ifPresent(arg ->
                        currencies.resolve(arg.currency()).deposit(ctx.viewer().uuid(), arg.amount()));
    }

    /** Withdraw {@code <amount>} of {@code [currency-spec]} from the viewer; a bad amount warns+skips. */
    private static void takeMoney(MenuActionContext ctx, Currencies currencies, Logger log) {
        money(ctx, "take-money", log)
                .ifPresent(arg ->
                        currencies.resolve(arg.currency()).withdraw(ctx.viewer().uuid(), arg.amount()));
    }

    /**
     * Move the viewer's balance in {@code [currency-spec]} to exactly {@code <amount>} by depositing or withdrawing
     * the delta: a shortfall is deposited, a surplus is withdrawn, an already-matching balance is left alone. A bad
     * amount warns and skips.
     */
    private static void setMoney(MenuActionContext ctx, Currencies currencies, Logger log) {
        money(ctx, "set-money", log).ifPresent(arg -> {
            CurrencyProvider provider = currencies.resolve(arg.currency());
            UUID viewer = ctx.viewer().uuid();
            double delta = arg.amount() - provider.balance(viewer);
            if (delta > 0) {
                provider.deposit(viewer, delta);
            } else if (delta < 0) {
                provider.withdraw(viewer, -delta);
            }
        });
    }

    /** Deposit {@code <amount>} onto the viewer's PlayerPoints balance; a convenience alias for the pinned currency. */
    private static void givePoints(MenuActionContext ctx, Currencies currencies, Logger log) {
        money(ctx, "give-points", log)
                .ifPresent(arg ->
                        currencies.resolve("playerpoints").deposit(ctx.viewer().uuid(), arg.amount()));
    }

    /** Withdraw {@code <amount>} from the viewer's PlayerPoints balance; a convenience alias for the pinned currency. */
    private static void takePoints(MenuActionContext ctx, Currencies currencies, Logger log) {
        money(ctx, "take-points", log)
                .ifPresent(arg ->
                        currencies.resolve("playerpoints").withdraw(ctx.viewer().uuid(), arg.amount()));
    }

    /** Parse the {@code <amount> [currency-spec]} argument, warning through {@code log} when the amount is unusable. */
    private static Optional<MoneyArg> money(MenuActionContext ctx, String action, Logger log) {
        Optional<MoneyArg> parsed = MoneyArg.parse(ctx.arg());
        if (parsed.isEmpty()) {
            log.warn("event=menu_economy_action_skipped action={} reason=malformed_amount value={}", action, ctx.arg());
        }
        return parsed;
    }

    // --- experience --------------------------------------------------------------------------------------------------

    /** Grant {@code <amount>} experience points to the viewer via Paper's native experience API. */
    private static void giveExp(MenuActionContext ctx, Logger log) {
        count(ctx, "give-exp", log).ifPresent(amount -> ctx.player().giveExp(amount));
    }

    /**
     * Remove {@code <amount>} experience points from the viewer, bounded by their current total so the removal never
     * drives experience below zero. {@code giveExp} of a negative that would underflow the total is otherwise an
     * error on Paper. A player with less than {@code amount} is simply emptied.
     */
    private static void takeExp(MenuActionContext ctx, Logger log) {
        count(ctx, "take-exp", log).ifPresent(amount -> {
            Player player = ctx.player();
            int take = Math.min(amount, Math.max(0, player.getTotalExperience()));
            player.giveExp(-take);
        });
    }

    /** Grant {@code <amount>} whole experience levels to the viewer. */
    private static void giveLevels(MenuActionContext ctx, Logger log) {
        count(ctx, "give-levels", log).ifPresent(amount -> ctx.player().giveExpLevels(amount));
    }

    /** Remove {@code <amount>} whole experience levels from the viewer, clamping the resulting level at zero. */
    private static void takeLevels(MenuActionContext ctx, Logger log) {
        count(ctx, "take-levels", log).ifPresent(amount -> {
            Player player = ctx.player();
            player.setLevel(Math.max(0, player.getLevel() - amount));
        });
    }

    /** Parse a whole non-negative {@code <amount>}, warning through {@code log} when it is missing or unusable. */
    private static OptionalInt count(MenuActionContext ctx, String action, Logger log) {
        OptionalInt parsed = parseCount(ctx.arg());
        if (parsed.isEmpty()) {
            log.warn("event=menu_economy_action_skipped action={} reason=malformed_amount value={}", action, ctx.arg());
        }
        return parsed;
    }

    // --- permission --------------------------------------------------------------------------------------------------

    /** Grant {@code <node>} to the viewer through Vault; a blank node or an absent Vault permission service is a no-op. */
    private static void givePerm(MenuActionContext ctx, PermissionQuery permissions) {
        String node = ctx.arg().strip();
        if (node.isEmpty()) {
            return; // no node to grant. Nothing to do, but not an error
        }
        permissions.add(ctx.viewer().uuid(), node);
    }

    /** Revoke {@code <node>} from the viewer through Vault; a blank node or an absent permission service is a no-op. */
    private static void takePerm(MenuActionContext ctx, PermissionQuery permissions) {
        String node = ctx.arg().strip();
        if (node.isEmpty()) {
            return;
        }
        permissions.remove(ctx.viewer().uuid(), node);
    }

    /** Parse a whole non-negative count; empty when the value is blank, non-numeric or negative. Exposed for tests. */
    static OptionalInt parseCount(String value) {
        try {
            int amount = Integer.parseInt(value.strip());
            return amount < 0 ? OptionalInt.empty() : OptionalInt.of(amount);
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }

    /**
     * A parsed {@code <amount> [currency-spec]} money argument: the first whitespace-delimited token is a
     * non-negative amount, and the rest (blank when absent) is the currency spec {@link Currencies#resolve(String)}
     * reads: a blank spec is the configured default. Parsing is Bukkit-free so plain-JUnit grammar tests can
     * exercise it; a missing, non-numeric, negative or non-finite amount yields an empty result, which the actions
     * treat as a fail-soft warn-and-skip.
     */
    public record MoneyArg(double amount, String currency) {

        public MoneyArg {
            Objects.requireNonNull(currency, "currency");
        }

        public static Optional<MoneyArg> parse(String value) {
            Objects.requireNonNull(value, "value");
            String trimmed = value.strip();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            String[] parts = trimmed.split("\\s+", 2);
            return parseAmount(parts[0]).map(amount -> new MoneyArg(amount, parts.length > 1 ? parts[1].strip() : ""));
        }

        /** The non-negative, finite amount in {@code token}, or empty when it is not a usable number. */
        private static Optional<Double> parseAmount(String token) {
            try {
                double amount = Double.parseDouble(token);
                if (amount < 0 || !Double.isFinite(amount)) {
                    return Optional.empty();
                }
                return Optional.of(amount);
            } catch (NumberFormatException notANumber) {
                return Optional.empty();
            }
        }
    }
}
