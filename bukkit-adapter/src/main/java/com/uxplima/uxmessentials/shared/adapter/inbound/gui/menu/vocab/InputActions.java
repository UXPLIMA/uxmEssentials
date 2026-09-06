package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The input slice of the action vocabulary: it registers the {@code input} and {@code confirm} ids so a spec that
 * writes {@code input:<key>} or {@code confirm:<key>} passes load-time validation the same way {@code close} or
 * {@code open} does. Registered once at startup into the shared {@link MenuBindings} alongside {@link MenuVocabulary}
 * and the other action packs.
 *
 * <p>The real behaviour of these two steps is <em>not</em> here. It lives in the click dispatcher, which reads the
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Continuation Continuation} the loader attaches
 * to the ref and splits the action chain around it, because their outcome arrives on a later callback and cannot run
 * inline through a plain action handler. On the flat success path the dispatcher intercepts the step before it ever
 * reaches these handlers, so they only fire when the step was written somewhere the dispatcher does not intercept, an
 * else-ladder, a deny list, or a per-requirement action list, where a continuation is unsupported. There the handler
 * logs a one-line operator warning and no-ops, so a mis-placed {@code input:}/{@code confirm:} degrades to a clear
 * diagnostic rather than a silent nothing. The warning is operator-facing only; no player text is produced, so no
 * {@code MessageKey} is involved.
 */
public final class InputActions {

    private InputActions() {}

    /** Register the {@code input} and {@code confirm} marker actions into {@code bindings}. */
    public static void register(MenuBindings bindings, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(log, "log");
        bindings.action("input", unsupportedHere("input", log));
        bindings.action("confirm", unsupportedHere("confirm", log));
    }

    private static Consumer<MenuActionContext> unsupportedHere(String id, Logger log) {
        return ctx -> log.warn(
                "menu action '{}' is only valid as a top-level click step, not inside an else/deny/requirement chain; ignored",
                id);
    }
}
