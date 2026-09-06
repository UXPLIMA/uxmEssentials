package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataStore;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataStore.NumericOp;

/**
 * The player-data slice of the menu action vocabulary: the ways a click (or an operator's {@code /menu} spec) can
 * write player-scoped state without a feature wiring it. Two backing stores sit behind one grammar, the durable,
 * database-backed {@link PlayerDataStore} (survives a restart or a world rollback) reached by the {@code data-*}
 * actions, and the transient per-holder PDC {@link PlayerMeta} reached by the {@code meta-*} actions. Registered
 * once at startup into the shared {@link MenuBindings} alongside {@link MenuVocabulary} and the other action packs,
 * so a disk-loaded spec resolves {@code data-add} / {@code meta-set} the same way a code-registered feature menu does.
 *
 * <p>The keys and values are operator config, so no {@code MessageKey} is involved; the only text any action produces
 * is a diagnostic line on the operator {@code log} when a numeric operand is malformed. The numeric actions route
 * through {@link PlayerDataStore#apply} / {@link PlayerMeta#add}, which read a missing or non-numeric stored value as
 * zero, so an operator may {@code data-add} onto a key that does not exist yet; {@code data-div} by zero is a
 * documented no-change rather than an error.
 *
 * <p>Every action is fail-soft: a blank key or a malformed operand is a logged-or-silent no-op, and {@link #safe}
 * turns anything a call unexpectedly throws into a logged no-op too. One bad effect must not abort the rest of a
 * chain. The {@code data-*} writes go through the store, which mutates its cache on the entity thread and persists
 * off-tick; the {@code meta-*} writes touch the online viewer's PDC on the entity thread, where the click already
 * runs: no blocking is added.
 */
public final class DataActions {

    private DataActions() {}

    /**
     * Register the data actions into {@code bindings}. {@code playerData} is the durable player-data store the
     * {@code data-*} actions write through; {@code playerMeta} is the PDC accessor the {@code meta-*} actions write
     * through on the online viewer; {@code log} is the operator console logger a fail-soft or malformed action warns
     * through. Left separate from {@link MenuVocabulary#registerActions} so that method's existing call-sites stay
     * untouched: the composition root calls both.
     */
    public static void register(MenuBindings bindings, PlayerDataStore playerData, PlayerMeta playerMeta, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(playerData, "playerData");
        Objects.requireNonNull(playerMeta, "playerMeta");
        Objects.requireNonNull(log, "log");
        registerData(bindings, playerData, log);
        registerMeta(bindings, playerMeta, log);
    }

    /** {@code data-set} (alias {@code set-data}), the four {@code data-<op>} arithmetic actions and {@code data-remove}. */
    private static void registerData(MenuBindings bindings, PlayerDataStore playerData, Logger log) {
        Consumer<MenuActionContext> set = safe("data-set", log, ctx -> dataSet(ctx, playerData));
        bindings.action("data-set", set);
        bindings.action("set-data", set);
        bindings.action("data-add", safe("data-add", log, ctx -> dataApply(ctx, playerData, NumericOp.ADD, log)));
        bindings.action("data-sub", safe("data-sub", log, ctx -> dataApply(ctx, playerData, NumericOp.SUB, log)));
        bindings.action("data-mul", safe("data-mul", log, ctx -> dataApply(ctx, playerData, NumericOp.MUL, log)));
        bindings.action("data-div", safe("data-div", log, ctx -> dataApply(ctx, playerData, NumericOp.DIV, log)));
        bindings.action("data-remove", safe("data-remove", log, ctx -> dataRemove(ctx, playerData)));
    }

    /** {@code meta-set}, {@code meta-add} and {@code meta-remove} over the online viewer's PDC. */
    private static void registerMeta(MenuBindings bindings, PlayerMeta playerMeta, Logger log) {
        bindings.action("meta-set", safe("meta-set", log, ctx -> metaSet(ctx, playerMeta)));
        bindings.action("meta-add", safe("meta-add", log, ctx -> metaAdd(ctx, playerMeta, log)));
        bindings.action("meta-remove", safe("meta-remove", log, ctx -> metaRemove(ctx, playerMeta)));
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
                log.warn("event=menu_data_action_failed action={} value={}", action, ctx.arg());
            }
        };
    }

    // --- durable player data
    // ------------------------------------------------------------------------------------------

    /** Store {@code <value…>} (spaces preserved) under {@code <key>}; a blank key is a silent no-op. */
    private static void dataSet(MenuActionContext ctx, PlayerDataStore playerData) {
        KeyValue arg = KeyValue.parse(ctx.arg());
        if (arg.key().isEmpty()) {
            return; // no key to write. Nothing to do, but not an error
        }
        playerData.set(ctx.viewer().uuid(), arg.key(), arg.value());
    }

    /** Apply {@code op} of {@code <number>} against {@code <key>}; a blank key or a malformed operand warns and skips. */
    private static void dataApply(MenuActionContext ctx, PlayerDataStore playerData, NumericOp op, Logger log) {
        KeyValue arg = KeyValue.parse(ctx.arg());
        if (arg.key().isEmpty()) {
            return;
        }
        OptionalDouble operand = parseNumber(arg.value());
        if (operand.isEmpty()) {
            log.warn("event=menu_data_skipped action=data-{} reason=malformed_number value={}", op, ctx.arg());
            return;
        }
        playerData.apply(ctx.viewer().uuid(), arg.key(), op, operand.getAsDouble());
    }

    /** Remove {@code <key>} from the viewer's durable data; a blank key is a silent no-op. */
    private static void dataRemove(MenuActionContext ctx, PlayerDataStore playerData) {
        String key = ctx.arg().strip();
        if (key.isEmpty()) {
            return;
        }
        playerData.remove(ctx.viewer().uuid(), key);
    }

    // --- transient PDC meta
    // -------------------------------------------------------------------------------------------

    /** Store {@code <value…>} (spaces preserved) under {@code <key>} in the viewer's PDC; a blank key is a no-op. */
    private static void metaSet(MenuActionContext ctx, PlayerMeta playerMeta) {
        KeyValue arg = KeyValue.parse(ctx.arg());
        if (arg.key().isEmpty()) {
            return;
        }
        playerMeta.set(ctx.player(), arg.key(), arg.value());
    }

    /** Add {@code <number>} to the counter under {@code <key>} in the viewer's PDC; a bad operand warns and skips. */
    private static void metaAdd(MenuActionContext ctx, PlayerMeta playerMeta, Logger log) {
        KeyValue arg = KeyValue.parse(ctx.arg());
        if (arg.key().isEmpty()) {
            return;
        }
        OptionalDouble delta = parseNumber(arg.value());
        if (delta.isEmpty()) {
            log.warn("event=menu_data_skipped action=meta-add reason=malformed_number value={}", ctx.arg());
            return;
        }
        playerMeta.add(ctx.player(), arg.key(), delta.getAsDouble());
    }

    /** Remove {@code <key>} from the viewer's PDC; a blank key is a silent no-op. */
    private static void metaRemove(MenuActionContext ctx, PlayerMeta playerMeta) {
        String key = ctx.arg().strip();
        if (key.isEmpty()) {
            return;
        }
        playerMeta.remove(ctx.player(), key);
    }

    /** The finite {@code double} in {@code token}, or empty when it is blank, non-numeric or non-finite. Exposed for tests. */
    public static OptionalDouble parseNumber(String token) {
        try {
            double parsed = Double.parseDouble(token.strip());
            return Double.isFinite(parsed) ? OptionalDouble.of(parsed) : OptionalDouble.empty();
        } catch (NumberFormatException notANumber) {
            return OptionalDouble.empty();
        }
    }

    /**
     * A parsed {@code <key> <value…>} argument: the first whitespace-delimited token is the key and everything after
     * it, with its internal spacing preserved, is the value. The numeric actions read the value as a single number;
     * {@code data-set} / {@code meta-set} keep it verbatim, so {@code name Steve Jobs} stores {@code Steve Jobs}.
     * Parsing is Bukkit-free so plain-JUnit grammar tests can exercise it; a blank argument yields an empty key, which
     * every action treats as a no-op.
     */
    public record KeyValue(String key, String value) {

        public KeyValue {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }

        public static KeyValue parse(String value) {
            Objects.requireNonNull(value, "value");
            String trimmed = value.strip();
            if (trimmed.isEmpty()) {
                return new KeyValue("", "");
            }
            String[] parts = trimmed.split("\\s+", 2);
            return new KeyValue(parts[0], parts.length > 1 ? parts[1] : "");
        }
    }
}
