package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The navigation router that links the bank menus, replacing the mutable setter cross-references they used to hold.
 * Each view takes a {@code Supplier<BankNavigation>} in its constructor (a final, non-null field) and dereferences
 * it only when a button is clicked, by which point the router is fully built. This keeps the
 * constructor-injection-only rule (no post-construction setters) while still allowing the menus to open each other,
 * since the router and the views it holds cannot all be constructed in one pass.
 *
 * <p>The bank list, the actions panel, and the members list are all engine-rendered menus now; the router carries
 * the {@link BankListMenu}, the {@link BankActionsMenu}, and the {@link BankMembersMenu} so the list's bank click
 * opens the actions panel, the panel's "back" reopens the list, and its "members" button opens the members grid.
 */
@NullMarked
public record BankNavigation(
        BankListMenu bankListMenu, BankActionsMenu bankActionsView, BankMembersMenu bankMembersMenu) {

    public BankNavigation {
        Objects.requireNonNull(bankListMenu, "bankListMenu");
        Objects.requireNonNull(bankActionsView, "bankActionsView");
        Objects.requireNonNull(bankMembersMenu, "bankMembersMenu");
    }
}
