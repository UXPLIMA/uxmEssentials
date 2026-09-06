package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.port.CrossServerTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link CrossServerTeleport} implementation: the send half of a cross-server player-warp hop. Given a warp
 * the access gate has admitted (and, for a priced non-member, already charged), it records the intent in the
 * shared {@link PendingTeleportStore} and asks the proxy to move the player to the warp's backend through the
 * existing {@link ServerConnector}. The target backend's join handler then completes the local hop and clears
 * the row. When the proxy channel is unregistered (a single server with no proxy in front), nothing is recorded:
 * the player is told the warp is unreachable and any charge is refunded, since they never moved.
 *
 * <p>The gate runs off the tick thread, so the store write is safe here; the {@code connect} plugin message rides
 * the player's own connection, so it is marshalled back onto the player's entity region through the {@link
 * Scheduler} port (Folia-safe) and skipped if they logged off in between. The refund goes through the same {@link
 * PlayerWarpEconomy} seam the charge used, so the debit and its reversal share one DB-backed, currency-aware path.
 */
@NullMarked
public final class BukkitCrossServerTeleport implements CrossServerTeleport {

    private final PendingTeleportStore store;
    private final ServerConnector connector;
    private final Scheduler scheduler;
    private final Optional<PlayerWarpEconomy> economy;
    private final Notifier notifier;
    private final String localServerId;
    private final Clock clock;

    public BukkitCrossServerTeleport(
            PendingTeleportStore store,
            ServerConnector connector,
            Scheduler scheduler,
            Optional<PlayerWarpEconomy> economy,
            Notifier notifier,
            String localServerId,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.localServerId = Objects.requireNonNull(localServerId, "localServerId");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void send(PlayerRef who, PlayerWarp warp, Optional<WarpCost> charged) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(charged, "charged");
        String target = warp.serverId().orElseThrow();
        if (!connector.isAvailable()) {
            notifier.send(
                    who,
                    PlayerwarpsMessageKey.PWARP_CROSS_SERVER_UNAVAILABLE,
                    Map.of("warp", warp.name().value(), "server", target));
            refund(who, charged);
            return;
        }
        store.record(new PendingTeleport(
                who.uuid(), warp.id().orElseThrow(), target, localServerId, clock.instant(), charged));
        notifier.send(
                who,
                PlayerwarpsMessageKey.PWARP_CROSS_SERVER_SENDING,
                Map.of("warp", warp.name().value(), "server", target));
        scheduler.onEntity(who, () -> connect(who.uuid(), target));
    }

    /** On the player's region thread: connect the still-online player to {@code target}, or skip if they left. */
    private void connect(UUID player, String target) {
        Player online = Bukkit.getPlayer(player);
        if (online != null && online.isOnline()) {
            connector.connect(online, target);
        }
    }

    /** Credit the charge back to the visitor; a no-op when nothing was charged or no economy is wired. */
    private void refund(PlayerRef who, Optional<WarpCost> charged) {
        if (economy.isEmpty()) {
            return;
        }
        charged.ifPresent(cost -> economy.get().refund(who, cost.amount(), cost.currencyId()));
    }
}
