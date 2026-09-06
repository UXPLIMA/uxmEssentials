package com.uxplima.uxmessentials.teleport.application;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.RespawnChain;
import com.uxplima.uxmessentials.teleport.domain.RespawnStep;

/**
 * The {@code DEATH_MANAGEMENT} respawn use case: walk the per-world {@link RespawnChain} on death and
 * return the first step that resolves to a valid {@link Position}. The chain is read from
 * {@link TeleportSettings#respawnChain(WorldRef)}; resolving each step (the world spawn, the urgent RTP
 * queue, the player's home, a named warp, the bed/anchor) is delegated to the {@link StepResolver} the
 * adapter supplies, since those targets live behind ports. The urgent RTP path is used for the
 * {@code tpr} step so a respawn never blocks on a queue refill.
 *
 * <p>An empty result means no step resolved and the caller defers to vanilla respawn. The settings view supplies
 * the global default chain unless death handling is disabled or a world explicitly overrides it.
 */
public final class ResolveRespawn {

    private final TeleportSettings settings;

    public ResolveRespawn(TeleportSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Resolve where the player respawns in {@code deathWorld}, walking the configured chain in order via
     * {@code resolver}. The resolver carries every port the steps need (spawn directory, RTP queue, home
     * lookup, warp directory); this use case owns only the ordering and the first-resolvable rule.
     */
    public Optional<Position> resolve(WorldRef deathWorld, StepResolver resolver) {
        Objects.requireNonNull(deathWorld, "deathWorld");
        Objects.requireNonNull(resolver, "resolver");
        RespawnChain chain = settings.respawnChain(deathWorld);
        Function<RespawnStep, Optional<Position>> fn = step -> resolver.resolve(deathWorld, step);
        return chain.resolve(fn);
    }

    /**
     * Resolves one {@link RespawnStep} to a concrete {@link Position} for a death world, or empty when
     * that step has no target for this player. Supplied by the adapter, which owns the port calls.
     */
    @FunctionalInterface
    public interface StepResolver {

        /** Resolve {@code step} in {@code deathWorld}, or empty when it does not apply. */
        Optional<Position> resolve(WorldRef deathWorld, RespawnStep step);
    }
}
