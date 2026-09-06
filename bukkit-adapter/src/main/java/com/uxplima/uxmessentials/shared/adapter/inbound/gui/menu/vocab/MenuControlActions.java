package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The menu-control slice of the action vocabulary: the ways a click can drive the very window it fired in rather
 * than the world outside it. Registered once at startup into the shared {@link MenuBindings} alongside
 * {@link MenuVocabulary} and the other action packs, so a disk-loaded spec resolves {@code refresh} /
 * {@code refresh-slot} / {@code reset-pagination} the same way a code-registered feature menu does.
 *
 * <ul>
 *   <li>{@code refresh} repaints the whole menu at its current page. The way a spec whose items read a live value
 *       (a balance, a toggle the click just flipped) shows the new state without the viewer reopening it.
 *   <li>{@code refresh-slot:<n>} repaints so slot {@code n} redraws; an out-of-range or malformed slot is a no-op.
 *   <li>{@code reset-pagination} (alias {@code reset-page}) resets the menu to page zero, the way a filter or search
 *       change returns a paginated menu to its first page rather than a now-empty later one.
 * </ul>
 *
 * <p>Each action reaches the window through the {@link MenuActionContext#control()} the live click supplied; a
 * context built outside a click carries a no-op control, so the same handler is a harmless no-op there. Every effect
 * is fail-soft: {@link #safe} turns anything a call unexpectedly throws into a one-line operator warning and a no-op,
 * so one bad control action never aborts the rest of a click's chain. The slot is operator config, so no
 * {@code MessageKey} is involved; the only text produced is a diagnostic on the operator {@code log}.
 */
public final class MenuControlActions {

    private MenuControlActions() {}

    /**
     * Register the menu-control actions into {@code bindings}. {@code log} is the operator console logger a fail-soft
     * action warns through. Left separate from {@link MenuVocabulary#registerActions} so that method's existing
     * call-sites stay untouched: the composition root calls both.
     */
    public static void register(MenuBindings bindings, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(log, "log");
        bindings.action("refresh", safe("refresh", log, ctx -> ctx.control().refresh()));
        bindings.action("refresh-slot", safe("refresh-slot", log, MenuControlActions::refreshSlot));
        Consumer<MenuActionContext> reset =
                safe("reset-pagination", log, ctx -> ctx.control().resetPagination());
        bindings.action("reset-pagination", reset);
        bindings.action("reset-page", reset);
    }

    /** Repaint the slot named by {@code refresh-slot:<n>}; a blank or non-numeric slot is a fail-soft skip. */
    private static void refreshSlot(MenuActionContext ctx) {
        parseSlot(ctx.arg()).ifPresent(slot -> ctx.control().refreshSlot(slot));
    }

    /**
     * The whole-number slot in {@code value}, or empty when it is blank or not a whole number, the signal to skip.
     * Bukkit-free and side-effect-free (a negative slot parses and is left for the control to reject as out-of-range),
     * so a plain-JUnit grammar test can exercise it.
     */
    public static OptionalInt parseSlot(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return OptionalInt.of(Integer.parseInt(value.strip()));
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a no-op rather
     * than escaping into the click dispatch: the same fail-soft contract the sibling action packs apply.
     */
    private static Consumer<MenuActionContext> safe(String action, Logger log, Consumer<MenuActionContext> body) {
        return ctx -> {
            try {
                body.accept(ctx);
            } catch (RuntimeException failure) {
                log.warn("event=menu_control_action_failed action={} value={}", action, ctx.arg());
            }
        };
    }
}
