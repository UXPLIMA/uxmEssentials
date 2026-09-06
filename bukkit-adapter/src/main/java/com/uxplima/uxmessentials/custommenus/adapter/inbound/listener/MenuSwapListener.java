package com.uxplima.uxmessentials.custommenus.adapter.inbound.listener;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;

/**
 * Opens a configured menu when a player swaps their hand items (the F key) instead of performing the swap. The menu
 * is read live from the same swapped {@link Supplier} the openers use, so a {@code /menu reload} that changes or
 * clears {@code swap-offhand-menu} takes effect on the next swap. When no swap menu is configured, or the configured
 * one is no longer a registered menu: the event is left alone and the vanilla hand swap happens as normal.
 *
 * <p>The event fires on the swapping player's own region thread and the {@link Menus} facade opens the window on that
 * same thread; the handler touches only that one player and never scans the roster, so it stays Folia-safe without an
 * extra hop.
 */
public final class MenuSwapListener implements Listener {

    private final Menus menus;
    private final Supplier<Optional<String>> swapMenu;
    private final Supplier<List<String>> menuNames;

    public MenuSwapListener(Menus menus, Supplier<Optional<String>> swapMenu, Supplier<List<String>> menuNames) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.swapMenu = Objects.requireNonNull(swapMenu, "swapMenu");
        this.menuNames = Objects.requireNonNull(menuNames, "menuNames");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Optional<String> menu = swapMenu.get();
        if (menu.isEmpty() || !menuNames.get().contains(menu.get())) {
            return;
        }
        event.setCancelled(true);
        menus.open(BukkitRefs.toRef(event.getPlayer()), menu.get(), null);
    }
}
