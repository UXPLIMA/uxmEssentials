package com.uxplima.uxmessentials.tablist.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.tablist.adapter.outbound.TablistRenderer;
import org.jspecify.annotations.NullMarked;

/**
 * Renders a player's tablist header/footer the moment they join so they do not wait a full refresh interval for the
 * first paint, and clears it on quit so a dropped player leaves no stale header/footer behind. Both events fire on the
 * joining/quitting player's region thread, so reading the live player and touching their tablist is region-local, no
 * scheduler hop is needed here.
 *
 * <p>The join also repaints every already-skinned online player's packet entry to the joiner: native Paper replicates a
 * player's list name and order to a late joiner, but the custom-skin packet path does not, so without this the joiner
 * would see real skins on every player who was already skinned before they connected.
 */
@NullMarked
public final class TablistConnectionListener implements Listener {

    private final TablistRenderer renderer;

    public TablistConnectionListener(TablistRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        renderer.renderFor(player);
        // The joiner's own row (incl. their own custom skin) is painted by renderFor above; this re-sends the
        // already-skinned players' rows to them so a late joiner does not see those players' real skins.
        renderer.repaintSkinsFor(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        renderer.forget(player);
    }
}
