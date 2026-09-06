package com.uxplima.uxmessentials.teleport.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * The {@code DEATH_MANAGEMENT} respawn-chain value object: a per-world <em>ordered</em> list of
 * {@link RespawnStep}s walked on death until one resolves to a valid location. The first resolvable
 * target wins, so an operator composes respawn behaviour declaratively per world
 * ({@code respawn.chain.<world>} = {@code ["rtp", "spawn", "home", "bed", "warp:hub", "anchor"]}).
 *
 * <p>The chain itself is pure ordering and the walk algorithm; <em>resolving</em> a step to a concrete
 * {@link Position} (reading the world spawn, the player's home, the warp directory, the urgent RTP
 * queue) is a port concern the use case supplies as a resolver function. {@link #resolve} applies that
 * resolver step by step and returns the first non-empty result, mirroring the upstream
 * "death management" behaviour under the project's own {@code RespawnChain} noun.
 */
public record RespawnChain(List<RespawnStep> steps) {

    public RespawnChain {
        Objects.requireNonNull(steps, "steps");
        steps = List.copyOf(steps);
    }

    /** Parse a delimited chain token list ({@code tpr;spawn;home}); unknown tokens are skipped. */
    public static RespawnChain parse(List<String> tokens) {
        Objects.requireNonNull(tokens, "tokens");
        List<RespawnStep> parsed = new ArrayList<>(tokens.size());
        for (String token : tokens) {
            RespawnStep.parse(token).ifPresent(parsed::add);
        }
        return new RespawnChain(parsed);
    }

    /** True when the chain names no steps: the caller falls back to vanilla respawn. */
    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /**
     * Walk the chain in order, asking {@code resolver} for each step's concrete position, and return
     * the first step that resolves. An empty result means no step produced a location and the caller
     * defers to vanilla respawn. The resolver carries every port dependency; the chain stays pure.
     */
    public Optional<Position> resolve(Function<RespawnStep, Optional<Position>> resolver) {
        Objects.requireNonNull(resolver, "resolver");
        for (RespawnStep step : steps) {
            Optional<Position> resolved = resolver.apply(step);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }
}
