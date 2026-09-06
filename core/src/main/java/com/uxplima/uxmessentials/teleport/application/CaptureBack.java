package com.uxplima.uxmessentials.teleport.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.port.BackLocationStore;
import com.uxplima.uxmessentials.teleport.domain.BackLocation;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.event.BackLocationCaptured;

/**
 * The {@code /back} use case: capture a return point before a teleport hop and on death, and return the
 * player to their most recent capture. Death-back is gated separately: {@code deathBackAllowed}
 * combines the operator's {@code back.on-death} setting with the player's
 * {@code uxmessentials.back.ondeath} permission, so a server can offer {@code /back} after a teleport
 * but not after dying.
 *
 * <p>Capture happens just before the hop (the pre-teleport position) so the most recent of (last
 * teleport, last death) is what {@code /back} returns to; the executor also captures the pre-teleport
 * position for its own audit, but this use case owns the player-facing return point.
 */
public final class CaptureBack {

    private final BackLocationStore store;
    private final TeleportEngine engine;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public CaptureBack(
            BackLocationStore store,
            TeleportEngine engine,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Record {@code from} as the pre-teleport return point, replacing any previous capture. */
    public void captureBeforeTeleport(PlayerRef who, Position from) {
        capture(who, BackLocation.beforeTeleport(from, clock.instant()));
    }

    /** Record {@code deathPoint} as the death return point (returnable only with death-back). */
    public void captureOnDeath(PlayerRef who, Position deathPoint) {
        capture(who, BackLocation.atDeath(deathPoint, clock.instant()));
    }

    /**
     * Return {@code who} to their captured location through the gated teleport machinery. Fails when no
     * capture exists, when the capture is a death point and {@code deathBackAllowed} is false, or when the
     * capture is a death point still inside the post-death delay ({@code deathDelaySeconds}); a teleport
     * capture is never delay-gated.
     */
    public Result<Unit, TeleportError> back(PlayerRef who, boolean deathBackAllowed, long deathDelaySeconds) {
        Objects.requireNonNull(who, "who");
        Optional<BackLocation> current = store.current(who);
        if (current.isEmpty()) {
            notifier.send(who, TeleportMessageKey.BACK_NONE);
            return Result.err(TeleportError.NO_BACK_LOCATION);
        }
        BackLocation location = current.get();
        Result<Position, TeleportError> target = location.resolveReturn(deathBackAllowed);
        if (target.isErr()) {
            notifier.send(who, TeleportMessageKey.BACK_DEATH_DENIED);
            return Result.err(target.errorOrThrow());
        }
        Optional<Long> wait = deathDelayRemaining(location, deathDelaySeconds);
        if (wait.isPresent()) {
            notifier.send(who, TeleportMessageKey.BACK_DEATH_DELAY, Map.of("seconds", Long.toString(wait.get())));
            return Result.err(TeleportError.BACK_ON_DEATH_DELAY);
        }
        engine.launch(who, Destination.at(target.orElseThrow()), TeleportKind.BACK);
        notifier.send(who, TeleportMessageKey.BACK_RETURNED);
        return Result.ok();
    }

    /**
     * Return {@code who} specifically to their last death point ({@code /deathback}). Unlike {@code /back},
     * which returns to the most recent of (teleport, death), this only acts on a death capture: a missing
     * capture or a non-death one (the player's last capture was a teleport) declines with no return, and a
     * death capture still honours the {@code deathBackAllowed} gate so the death-back permission governs both
     * commands the same way.
     */
    public Result<Unit, TeleportError> backToDeath(PlayerRef who, boolean deathBackAllowed, long deathDelaySeconds) {
        Objects.requireNonNull(who, "who");
        Optional<BackLocation> current = store.current(who);
        if (current.isEmpty() || !current.get().requiresDeathBack()) {
            notifier.send(who, TeleportMessageKey.DEATHBACK_NONE);
            return Result.err(TeleportError.NO_BACK_LOCATION);
        }
        BackLocation location = current.get();
        Result<Position, TeleportError> target = location.resolveReturn(deathBackAllowed);
        if (target.isErr()) {
            notifier.send(who, TeleportMessageKey.BACK_DEATH_DENIED);
            return Result.err(target.errorOrThrow());
        }
        Optional<Long> wait = deathDelayRemaining(location, deathDelaySeconds);
        if (wait.isPresent()) {
            notifier.send(who, TeleportMessageKey.BACK_DEATH_DELAY, Map.of("seconds", Long.toString(wait.get())));
            return Result.err(TeleportError.BACK_ON_DEATH_DELAY);
        }
        engine.launch(who, Destination.at(target.orElseThrow()), TeleportKind.BACK);
        notifier.send(who, TeleportMessageKey.BACK_RETURNED);
        return Result.ok();
    }

    /**
     * The remaining whole seconds before a death capture clears the post-death delay, or empty when the
     * return is allowed now: a teleport capture, a disabled delay ({@code deathDelaySeconds <= 0}), or a
     * death capture whose window has already elapsed.
     */
    private Optional<Long> deathDelayRemaining(BackLocation location, long deathDelaySeconds) {
        if (deathDelaySeconds <= 0 || !location.requiresDeathBack()) {
            return Optional.empty();
        }
        long elapsed = Duration.between(location.capturedAt(), clock.instant()).toSeconds();
        long remaining = deathDelaySeconds - elapsed;
        return remaining > 0 ? Optional.of(remaining) : Optional.empty();
    }

    private void capture(PlayerRef who, BackLocation location) {
        Objects.requireNonNull(who, "who");
        store.capture(who, location);
        events.publish(new BackLocationCaptured(who, location.position(), location.cause()));
    }
}
