package com.uxplima.uxmessentials.presence.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The presence lifecycle listener: it seeds a player's {@link PlayerPresence} on join and drops it on quit, so a
 * disconnected player holds no presence state. Vanish moved to its own {@code vanish} context, which owns the vanish
 * view and the join/quit re-hide and quit-line suppression through its own listener, so this listener no longer
 * touches visibility.
 */
@NullMarked
public final class PresenceLifecycleListener implements Listener {

    private final PresenceStore store;

    public PresenceLifecycleListener(PresenceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Seed the joining player's active presence so the AFK clock starts from now.
        store.current(BukkitRefs.toRef(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        store.forget(who);
    }
}
