package com.uxplima.uxmessentials.teleport.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A per-world {@code /spawn} redirect: a {@code /spawn} issued in {@link #sourceWorld} resolves to
 * {@link #targetWorld}'s spawn instead of the source's own. This lets an operator funnel several worlds
 * to one hub spawn without copying the location. Configured under {@code teleport.spawn.mirror.<world>}.
 *
 * <p>Only the redirect rule lives in the domain, the actual spawn {@code Position} for the target world
 * is read through the spawn directory port at resolve time, so a mirror never goes stale when the
 * target's spawn is moved.
 *
 * @param sourceWorld the world whose {@code /spawn} is redirected
 * @param targetWorld the world whose spawn is used instead
 */
public record SpawnMirror(UUID sourceWorld, UUID targetWorld) {

    public SpawnMirror {
        Objects.requireNonNull(sourceWorld, "sourceWorld");
        Objects.requireNonNull(targetWorld, "targetWorld");
        if (sourceWorld.equals(targetWorld)) {
            throw new IllegalArgumentException("a spawn mirror must point at a different world");
        }
    }
}
