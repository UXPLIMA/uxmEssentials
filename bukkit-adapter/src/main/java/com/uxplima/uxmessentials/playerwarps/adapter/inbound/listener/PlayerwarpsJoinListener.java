package com.uxplima.uxmessentials.playerwarps.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.playerwarps.application.CrossServerArrival;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Warms the joining player's owned player-warps into the repository cache so the {@code /pwarp} and
 * {@code /pwarp del} name-argument suggesters have the names in memory to complete by the time the player
 * starts typing. The suggesters run on the tick thread and must never block on the database, so they only
 * peek the cache; this listener does the one load up front, off the join thread via the {@link Scheduler}
 * port's async seam (the read hits SQLite). A player who never types a pwarp command still pays only one
 * cheap cached read whose entry expires on its own TTL.
 *
 * <p>When the {@code cross-server} sub-group is on, the same join is also the arrival point for a cross-server
 * teleport: the {@link CrossServerArrival} handler reads the shared pending-teleport row off-tick and, if it
 * targets this backend, completes the local hop. The handler is absent (an empty {@link Optional}) when the
 * sub-group is off, so a disabled sub-group does no arrival work at all, only the cache warm runs.
 */
@NullMarked
public final class PlayerwarpsJoinListener implements Listener {

    private final PlayerWarpRepository repository;
    private final Scheduler scheduler;
    private final Optional<CrossServerArrival> arrival;

    public PlayerwarpsJoinListener(
            PlayerWarpRepository repository, Scheduler scheduler, Optional<CrossServerArrival> arrival) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.arrival = Objects.requireNonNull(arrival, "arrival");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef who = BukkitRefs.toRef(event.getPlayer());
        scheduler.async(() -> repository.ownedBy(who));
        arrival.ifPresent(handler -> handler.onArrival(who));
    }
}
