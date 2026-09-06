package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Clock;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.application.port.BackLocationStore;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.BackLocation;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.event.PlayerTeleported;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link TeleportExecutor} on Paper's {@code teleportAsync}. The hop is driven from the player's own
 * entity scheduler (so it is valid on Folia), the destination's live follow target is re-read at the
 * moment of the hop, the pre-teleport position is captured for {@code /back}, and {@link PlayerTeleported}
 * is published on arrival. Passengers and vehicles are retained; the post-teleport invulnerability grace
 * window is the engine's concern, not this adapter's.
 *
 * <p>The completion callback may resume on any region/IO thread, so the success message and the
 * {@code /back} capture hop back to the player's region thread, the one place the teleport flow
 * deliberately hops more than once (docs/02-concurrency.md §6.5).
 */
@NullMarked
public final class AsyncTeleportExecutor implements TeleportExecutor {

    private final Scheduler scheduler;
    private final BackLocationStore backStore;
    private final DomainEventPublisher events;
    private final Logger log;
    private final Clock clock;
    private final BooleanSupplier centerToggle;
    private final TeleportArrivalHud arrivalHud;
    private final TeleportArrivalEffects arrivalEffects;

    public AsyncTeleportExecutor(
            Scheduler scheduler,
            BackLocationStore backStore,
            DomainEventPublisher events,
            Logger log,
            Clock clock,
            BooleanSupplier centerToggle,
            TeleportArrivalHud arrivalHud,
            TeleportArrivalEffects arrivalEffects) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.backStore = Objects.requireNonNull(backStore, "backStore");
        this.events = Objects.requireNonNull(events, "events");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.centerToggle = Objects.requireNonNull(centerToggle, "centerToggle");
        this.arrivalHud = Objects.requireNonNull(arrivalHud, "arrivalHud");
        this.arrivalEffects = Objects.requireNonNull(arrivalEffects, "arrivalEffects");
    }

    @Override
    public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
        // A plain hop (admin /tp, positional) has no post-landing side effect.
        teleport(who, destination, kind, AsyncTeleportExecutor::noOnLanded);
    }

    private static void noOnLanded() {
        // Nothing hangs off a plain hop's arrival.
    }

    @Override
    public void teleport(PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(onLanded, "onLanded");
        scheduler.onEntity(who, () -> hop(who, destination, kind, onLanded, true, true));
    }

    @Override
    public void relocate(PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(onLanded, "onLanded");
        scheduler.onEntity(who, () -> hop(who, destination, kind, onLanded, false, false));
    }

    private void hop(
            PlayerRef who,
            Destination destination,
            TeleportKind kind,
            Runnable onLanded,
            boolean captureBack,
            boolean showArrival) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return; // despawned between launch and hop: nothing to move
        }
        Position target = applyCentering(resolveTarget(destination));
        Location to = locationFor(target);
        if (to == null) {
            log.warn("teleport target world unloaded for {}", who.name());
            return;
        }
        Position from = TeleportRefs.positionOf(player);
        if (captureBack) {
            captureBack(who, from);
        }
        // teleportAsync retains passengers and vehicles by default and performs the region hop and async
        // chunk load internally; the entity scheduler is already on the player's region thread.
        var ignored = player.teleportAsync(to)
                .whenComplete((ok, err) -> onArrival(who, kind, from, target, ok, err, onLanded, showArrival));
    }

    private void onArrival(
            PlayerRef who,
            TeleportKind kind,
            Position from,
            Position to,
            Boolean ok,
            Throwable err,
            Runnable onLanded,
            boolean showArrival) {
        if (err != null) {
            log.warn("teleport failed for {}: {}", who.name(), String.valueOf(err.getMessage()));
            return;
        }
        if (Boolean.FALSE.equals(ok)) {
            return; // Paper refused the hop (e.g. unloaded target); the player stayed put: no landed callback
        }
        scheduler.onEntity(who, () -> {
            events.publish(new PlayerTeleported(who, kind, from, to));
            if (showArrival) {
                arrivalHud.arrived(who, kind);
                arrivalEffects.arrived(who, kind);
            }
            // The cooldown stamp / RTP charge / arrival grace hang off a real landing only, never a refusal.
            onLanded.run();
        });
    }

    private Position applyCentering(Position target) {
        return centerToggle.getAsBoolean() ? target.atBlockCenter() : target;
    }

    private Position resolveTarget(Destination destination) {
        return destination
                .follow()
                .map(follow -> liveOrSeed(follow, destination.position()))
                .orElse(destination.position());
    }

    private Position liveOrSeed(PlayerRef follow, Position seed) {
        Player live = Bukkit.getPlayer(follow.uuid());
        return live != null && live.isOnline() ? TeleportRefs.positionOf(live) : seed;
    }

    private void captureBack(PlayerRef who, Position from) {
        backStore.capture(who, BackLocation.beforeTeleport(from, clock.instant()));
    }

    private static @org.jspecify.annotations.Nullable Location locationFor(Position position) {
        World world = Bukkit.getWorld(position.world().uid());
        return world == null ? null : BukkitRefs.toLocation(world, position);
    }
}
