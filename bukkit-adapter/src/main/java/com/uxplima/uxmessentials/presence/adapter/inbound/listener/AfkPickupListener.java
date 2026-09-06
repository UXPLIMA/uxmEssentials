package com.uxplima.uxmessentials.presence.adapter.inbound.listener;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Cancels an AFK player's item pickups when the operator has opted into {@code anti-afk.disable-pickup-while-afk}
 *, so a player parked at an AFK farm does not vacuum its drops while away. Registered only when the toggle is
 * on; when off, this listener is never wired, so default behaviour is unchanged.
 *
 * <p>Both pickup entry points are covered: {@link PlayerAttemptPickupItemEvent} (the player's own attempt) and
 * {@link EntityPickupItemEvent} filtered to a {@link Player} (the entity-side path other plugins may trigger).
 * The AFK flag is read from the {@link PresenceStore}'s {@code ConcurrentHashMap}, safe on the region thread the
 * pickup fires on. The first cancelled pickup of an AFK session tells the player once through the
 * {@link Notifier}; the notice is suppressed for the rest of that session so a player standing on a pile
 * of drops is not spammed, and the per-player mark is dropped once they are no longer AFK so the next session
 * notifies afresh.
 */
@NullMarked
public final class AfkPickupListener implements Listener {

    private final PresenceStore store;
    private final Notifier notifier;
    private final Set<UUID> notified = ConcurrentHashMap.newKeySet();

    public AfkPickupListener(PresenceStore store, Notifier notifier) {
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (afkBlocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && afkBlocked(player)) {
            event.setCancelled(true);
        }
    }

    /** True when {@code player} is AFK (so the pickup is cancelled); notifies once per AFK session. */
    private boolean afkBlocked(Player player) {
        PlayerRef who = BukkitRefs.toRef(player);
        if (!store.current(who).afk()) {
            notified.remove(who.uuid());
            return false;
        }
        if (notified.add(who.uuid())) {
            notifier.send(who, PresenceMessageKey.AFK_PICKUP_BLOCKED);
        }
        return true;
    }
}
