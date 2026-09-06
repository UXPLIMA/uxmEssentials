package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.PendingTeleport;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelToggles;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link Warmups} decorator that registers every warmup the teleport engine begins with the
 * {@link WarmupTracker}, so the move/damage listeners can cancel it. The kernel's {@code SchedulerWarmups}
 * does the duration resolution and the countdown; this decorator only snapshots the player's origin block
 * and the configured cancel toggles at {@code begin} time and arms the tracker with the returned handle.
 *
 * <p>Wrapping the port, rather than threading the handle back through the engine, keeps the cooldown /
 * warmup orchestration in {@code :core} untouched while still giving the adapter the live handle the
 * move-cancels-warmup invariant needs. A zero-duration (bypass) warmup returns an already-complete handle
 * the tracker drops, so a bypassed teleport is correctly immune to move-cancel.
 *
 * <p>The wrapper also resolves the warmup's duration here, the same min-reducer the kernel
 * {@code SchedulerWarmups} applies, and records the resulting completion instant with the tracker, so the
 * {@code teleport_warmup_remaining} placeholder can read the remaining wait the kernel port does not surface
 * back through its handle.
 */
@NullMarked
public final class TrackingWarmups implements Warmups {

    private final Warmups delegate;
    private final WarmupTracker tracker;
    private final Supplier<WarmupCancelToggles> toggles;
    private final Permissions permissions;
    private final Clock clock;

    public TrackingWarmups(
            Warmups delegate,
            WarmupTracker tracker,
            Supplier<WarmupCancelToggles> toggles,
            Permissions permissions,
            Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        Optional<Position> origin = currentPosition(who);
        WarmupHandle handle = delegate.begin(who, kind, wrapComplete(who, onComplete), onCancel);
        origin.ifPresent(at -> arm(who, kind, at, handle));
        return handle;
    }

    private Runnable wrapComplete(PlayerRef who, Runnable onComplete) {
        return () -> {
            tracker.forget(who.uuid());
            onComplete.run();
        };
    }

    private void arm(PlayerRef who, WarmupKind kind, Position origin, WarmupHandle handle) {
        if (handle.isComplete()) {
            return;
        }
        // A real destination is not needed for the move comparison. The origin block is what the
        // invariant pins. The destination/kind fields are carried only for diagnostics, so a neutral
        // marker keeps the tracker honest without re-plumbing the engine's destination through the port.
        PendingTeleport warmup =
                PendingTeleport.arm(who, TeleportKind.ADMIN, origin, Destination.at(origin), toggles.get());
        tracker.arm(who, warmup, handle, clock.instant().plus(resolveDuration(who, kind)));
    }

    private Duration resolveDuration(PlayerRef who, WarmupKind kind) {
        if (permissions.has(who, kind.bypassNode())) {
            return Duration.ZERO;
        }
        QuotaFamily family = QuotaFamily.threshold(kind.warmupNode());
        long seconds = permissions
                .resolveQuota(who, family, null, kind.defaultSeconds())
                .orElse(kind.defaultSeconds());
        return Duration.ofSeconds(Math.max(0L, seconds));
    }

    private static Optional<Position> currentPosition(PlayerRef who) {
        Player player = Bukkit.getPlayer(who.uuid());
        return player == null || !player.isOnline() ? Optional.empty() : Optional.of(TeleportRefs.positionOf(player));
    }
}
