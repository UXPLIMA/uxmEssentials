package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListControlSyntax;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The paged-list-control slice of the action vocabulary: the bottom-bar buttons a browse menu drives its paged list's
 * sort, filter and search from. Registered once at startup into the shared {@link MenuBindings} alongside
 * {@link MenuVocabulary} and the other action packs, so a disk-loaded spec resolves {@code list-sort} /
 * {@code list-filter} / {@code list-search} the same way a code-registered feature menu does.
 *
 * <ul>
 *   <li>{@code list-sort:<listId>[:next|prev|reset]} advances (default), steps back, or resets the sort the list is
 *       drawn with, then re-queries its source for the first page.
 *   <li>{@code list-filter:<listId>:<key>=<value>} sets the filter {@code key} to {@code value} (an empty value clears
 *       it), then re-queries the first page. The value may carry a placeholder the engine resolves before the action
 *       runs, so a category button can pass its own category as {@code %argument_cat%}.
 *   <li>{@code list-search:<listId>:<key>} opens a text prompt and, on submit, stores the typed line as the filter
 *       {@code key}, then re-queries the first page; a cancel changes nothing.
 * </ul>
 *
 * <p>Each action names the source id of the paged list it targets ({@code list-sort:pw:browse}) because a menu may
 * hold more than one paged list; the id is required, never inferred. The real work. Mutating the list's query state
 * and re-querying, reaches the window through the {@link MenuActionContext#control()} the live click supplied, so it
 * rides the exact page-flip path (its in-flight flag, thread hops and repaint); a context built outside a live click
 * carries a no-op control, so the same handler is a harmless no-op there. Every effect is fail-soft: {@link #safe}
 * turns anything a call unexpectedly throws into a one-line operator warning and a no-op, and a value the grammar
 * cannot parse is a logged skip. The tokens are operator config, so no {@code MessageKey} is involved; the only text
 * produced is a diagnostic on the operator {@code log}.
 */
public final class ListControlActions {

    private ListControlActions() {}

    /**
     * Register the list-control actions into {@code bindings}. {@code log} is the operator console logger a fail-soft
     * or unparseable action warns through. Left separate from {@link MenuVocabulary#registerActions} so that method's
     * existing call-sites stay untouched: the composition root calls both.
     */
    public static void register(MenuBindings bindings, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(log, "log");
        bindings.action(ListControlSyntax.SORT_ACTION, safe(ListControlSyntax.SORT_ACTION, log, ctx -> sort(ctx, log)));
        bindings.action(
                ListControlSyntax.FILTER_ACTION, safe(ListControlSyntax.FILTER_ACTION, log, ctx -> filter(ctx, log)));
        bindings.action(
                ListControlSyntax.SEARCH_ACTION, safe(ListControlSyntax.SEARCH_ACTION, log, ctx -> search(ctx, log)));
    }

    private static void sort(MenuActionContext ctx, Logger log) {
        ListControlSyntax.parseSort(ctx.arg())
                .ifPresentOrElse(
                        ref -> ctx.control().sortList(ref.listId(), ref.direction()),
                        () -> warnUnparseable(ListControlSyntax.SORT_ACTION, ctx, log));
    }

    private static void filter(MenuActionContext ctx, Logger log) {
        ListControlSyntax.parseFilter(ctx.arg())
                .ifPresentOrElse(
                        ref -> ctx.control().filterList(ref.listId(), ref.key(), ref.value()),
                        () -> warnUnparseable(ListControlSyntax.FILTER_ACTION, ctx, log));
    }

    private static void search(MenuActionContext ctx, Logger log) {
        ListControlSyntax.parseSearch(ctx.arg())
                .ifPresentOrElse(
                        ref -> ctx.control().searchList(ref.listId(), ref.key()),
                        () -> warnUnparseable(ListControlSyntax.SEARCH_ACTION, ctx, log));
    }

    private static void warnUnparseable(String action, MenuActionContext ctx, Logger log) {
        log.warn("event=list_control_action_unparseable action={} value={}", action, ctx.arg());
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
                log.warn("event=list_control_action_failed action={} value={}", action, ctx.arg());
            }
        };
    }
}
