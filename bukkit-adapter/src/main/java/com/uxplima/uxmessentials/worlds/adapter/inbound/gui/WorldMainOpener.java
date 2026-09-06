package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * The seam {@code WorldsServices} exposes so {@code /world gui <world>} opens the engine-rendered per-world editor hub
 * without the command (or the services holder) depending on the menu engine directly. Worlds wiring binds it to
 * {@code WorldMainMenu.open} once that menu is built, the same indirection the world-list open seam uses.
 */
@FunctionalInterface
@NullMarked
public interface WorldMainOpener {

    /** Open the per-world editor hub for {@code world}; the menu self-schedules onto the viewer's entity thread. */
    void open(Player player, PlayerRef viewer, WorldName world);
}
