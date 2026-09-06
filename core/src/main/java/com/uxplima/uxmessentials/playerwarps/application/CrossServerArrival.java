package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpTeleporter;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The arrival half of a cross-server teleport. When a player joins this backend the inbound listener hands them
 * here; if the shared store holds a pending teleport that targets this backend and is still fresh, the warp is
 * resolved and the player is hopped locally after a short settling delay, exactly as if they had run
 * {@code /pwarp} here. A stale row (aimed at another backend, or older than the request TTL) is cleared instead
 * with the origin's charge refunded on expiry, and a warp that is gone, suspended, or has since moved off this
 * backend clears the row, refunds the charge, and tells the player it could not be reached.
 *
 * <p>Every store and repository read runs off the tick thread through the {@link Scheduler} port; only the final
 * local hop is marshalled back onto the player's entity region, so the live player is moved on their own region
 * thread (Folia-safe). The refund goes through the same {@link PlayerWarpEconomy} seam the origin charged on, so
 * the debit and its reversal share one currency-aware, DB-backed path.
 */
public final class CrossServerArrival {

    private final PendingTeleportStore store;
    private final PlayerWarpRepository repository;
    private final PlayerWarpTeleporter teleporter;
    private final Optional<PlayerWarpEconomy> economy;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final String localServerId;
    private final Duration arrivalDelay;
    private final Duration requestTtl;
    private final Clock clock;

    public CrossServerArrival(
            PendingTeleportStore store,
            PlayerWarpRepository repository,
            PlayerWarpTeleporter teleporter,
            Optional<PlayerWarpEconomy> economy,
            Notifier notifier,
            Scheduler scheduler,
            String localServerId,
            Duration arrivalDelay,
            Duration requestTtl,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.teleporter = Objects.requireNonNull(teleporter, "teleporter");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.localServerId = Objects.requireNonNull(localServerId, "localServerId");
        this.arrivalDelay = Objects.requireNonNull(arrivalDelay, "arrivalDelay");
        this.requestTtl = Objects.requireNonNull(requestTtl, "requestTtl");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Read the joining player's pending teleport off-tick and either schedule the hop or clear a stale row. */
    public void onArrival(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.async(() -> triage(who));
    }

    /** Off-tick: decide whether this backend owns the pending row, expiring or deferring to the delayed completion. */
    private void triage(PlayerRef who) {
        Optional<PendingTeleport> found = store.find(who.uuid());
        if (found.isEmpty()) {
            return;
        }
        PendingTeleport pending = found.get();
        if (isExpired(pending)) {
            store.clear(who.uuid());
            refund(who, pending);
            return;
        }
        if (!pending.targetServer().equals(localServerId)) {
            // A row aimed at another backend: this join is not its destination, so clear the stale intent without
            // moving the player. Expiry (above) is the only path that refunds, so a misrouted row is simply dropped.
            store.clear(who.uuid());
            return;
        }
        scheduler.asyncAfter(arrivalDelay, () -> complete(who, pending));
    }

    /** Off-tick after the settling delay: resolve the warp and either hop the player or refund and notify failure. */
    private void complete(PlayerRef who, PendingTeleport pending) {
        Optional<PlayerWarp> resolved = repository.findById(pending.warp());
        store.clear(who.uuid());
        if (isArrivable(resolved)) {
            PlayerWarp warp = resolved.orElseThrow();
            notifier.send(
                    who,
                    PlayerwarpsMessageKey.PWARP_CROSS_SERVER_ARRIVED,
                    Map.of("warp", warp.name().value()));
            scheduler.onEntity(who, () -> teleporter.teleportTo(who, warp));
            return;
        }
        refund(who, pending);
        notifier.send(who, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_FAILED, Map.of("warp", label(resolved, pending)));
    }

    /** True once the request has aged past the configured TTL, so a long-abandoned row is cleared not honoured. */
    private boolean isExpired(PendingTeleport pending) {
        return pending.requestedAt().plus(requestTtl).isBefore(clock.instant());
    }

    /** A warp is arrivable only if it still exists, is ACTIVE, and lives on this backend as of the read. */
    private boolean isArrivable(Optional<PlayerWarp> warp) {
        return warp.filter(w -> w.status() == WarpStatus.ACTIVE)
                .flatMap(PlayerWarp::serverId)
                .map(localServerId::equals)
                .orElse(false);
    }

    /** Credit the origin's charge back to the visitor; a no-op when nothing was charged or no economy is wired. */
    private void refund(PlayerRef who, PendingTeleport pending) {
        if (economy.isEmpty()) {
            return;
        }
        pending.paid().ifPresent(cost -> economy.get().refund(who, cost.amount(), cost.currencyId()));
    }

    /** The warp label for the failure notice: the resolved name when known, else the surrogate id. */
    private static String label(Optional<PlayerWarp> warp, PendingTeleport pending) {
        return warp.map(w -> w.name().value()).orElse("#" + pending.warp().value());
    }
}
