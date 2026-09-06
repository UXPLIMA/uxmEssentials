package com.uxplima.uxmessentials.teleport.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.SafeLocationQueue;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;

/**
 * The {@code /rtp} use case: serve a pre-validated safe location O(1) from the per-world queue, redirect
 * through the configured fallback world when the requested world has no queue, and hand the destination
 * to the gated teleport machinery. The off-thread safe-search is the queue's refill primitive, fired
 * here below the low-water mark, never an on-demand per-request scan. The requester never waits on a
 * chunk load.
 *
 * <p>Two entry points share the queue: {@link #background} (the manual {@code /rtp}. Cost and cooldown are
 * gated up front through {@link TeleportEngine#gateRandom}, then a location is served and handed to the
 * move-cancellable warmup) and {@link #firstJoin} (the involuntary respawn / first-join serve, which polls
 * the pool and teleports immediately with no warmup, cooldown, or charge). Neither ever runs a synchronous
 * search: an empty queue kicks a non-blocking refill and reports back rather than blocking the caller.
 */
public final class ResolveRtp {

    private final SafeLocationQueue queue;
    private final WorldLookup worlds;
    private final TeleportEngine engine;
    private final Notifier notifier;
    private final TeleportSettings settings;

    public ResolveRtp(
            SafeLocationQueue queue,
            WorldLookup worlds,
            TeleportEngine engine,
            Notifier notifier,
            TeleportSettings settings) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * The manual {@code /rtp}: redirect to the fallback world if needed, gate cost and cooldown <em>before</em>
     * consulting the queue (a player who cannot pay or is on cooldown never triggers a serve), then poll, fire a
     * refill, and hand a served location to the move-cancellable warmup. The cost is withdrawn and the cooldown
     * stamped only once the teleport lands (see {@link TeleportEngine#launchRandom}).
     */
    public Result<Unit, TeleportError> background(PlayerRef who, WorldRef requestedWorld) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(requestedWorld, "requestedWorld");
        Optional<WorldRef> resolved = resolveWorld(requestedWorld);
        if (resolved.isEmpty()) {
            notifier.send(who, TeleportMessageKey.RTP_DISALLOWED);
            return Result.err(TeleportError.RTP_WORLD_DISALLOWED);
        }
        Result<Unit, TeleportError> gate = engine.gateRandom(who);
        if (gate.isErr()) {
            return gate; // gateRandom already notified (cooldown / can't afford / jailed): queue untouched
        }
        WorldRef world = resolved.get();
        Optional<RtpSafeLocation> location = queue.poll(world);
        queue.requestRefill(world);
        if (location.isEmpty()) {
            notifier.send(who, TeleportMessageKey.RTP_NO_LOCATION);
            return Result.err(TeleportError.RTP_NO_SAFE_LOCATION);
        }
        engine.launchRandom(who, Destination.at(location.get().position()));
        return Result.ok();
    }

    /**
     * The involuntary first-join / respawn serve: poll the pre-warmed pool and teleport immediately (no warmup,
     * cooldown, or charge), or kick a non-blocking refill and report empty when the pool is momentarily drained.
     * Never a synchronous search. A drained pool simply leaves the player where they joined and warms for next
     * time. The arrival grace still applies on a successful serve.
     */
    public Result<Unit, TeleportError> firstJoin(PlayerRef who, WorldRef requestedWorld) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(requestedWorld, "requestedWorld");
        Optional<WorldRef> resolved = resolveWorld(requestedWorld);
        if (resolved.isEmpty()) {
            return Result.err(TeleportError.RTP_WORLD_DISALLOWED);
        }
        WorldRef world = resolved.get();
        Optional<RtpSafeLocation> location = queue.poll(world);
        queue.requestRefill(world);
        if (location.isEmpty()) {
            return Result.err(TeleportError.RTP_NO_SAFE_LOCATION);
        }
        engine.arriveRandomImmediately(who, Destination.at(location.get().position()));
        return Result.ok();
    }

    private Optional<WorldRef> resolveWorld(WorldRef requested) {
        if (queue.hasQueue(requested)) {
            return Optional.of(requested);
        }
        return settings.rtpFallbackWorld(requested).flatMap(worlds::findByName).filter(queue::hasQueue);
    }
}
