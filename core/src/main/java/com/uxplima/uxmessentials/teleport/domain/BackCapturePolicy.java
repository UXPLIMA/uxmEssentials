package com.uxplima.uxmessentials.teleport.domain;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The pure decision for whether a vanilla teleport overwrites a player's {@code /back} return point. A
 * player who throws an ender pearl or eats a chorus fruit usually wants {@code /back} to still point at
 * where they were <em>before</em> the pearl, not at the pearl's landing spot, so those causes are ignored
 * by default. Operators tune the ignored set through {@code teleport.conf}'s {@code back.ignored-causes}.
 *
 * <p>This governs the vanilla-teleport capture only. Plugin-driven hops ({@code /home}, {@code /warp},
 * {@code /tpa}, …) always capture through the teleport executor; those are intentional moves the player
 * issued and are never on this list.
 *
 * @param ignoredCauses the cause categories that must not overwrite the back point
 */
public record BackCapturePolicy(Set<TeleportCauseCategory> ignoredCauses) {

    public BackCapturePolicy {
        Objects.requireNonNull(ignoredCauses, "ignoredCauses");
        ignoredCauses = Set.copyOf(ignoredCauses);
    }

    /** The shipped default: ender pearls and chorus fruit do not clobber {@code /back}. */
    public static BackCapturePolicy defaults() {
        return new BackCapturePolicy(EnumSet.of(TeleportCauseCategory.ENDER_PEARL, TeleportCauseCategory.CHORUS_FRUIT));
    }

    /** A policy that captures on every cause: the pre-toggle behaviour, for operators who want it. */
    public static BackCapturePolicy captureAll() {
        return new BackCapturePolicy(Set.of());
    }

    /** True when a teleport of {@code cause} should record a fresh {@code /back} point. */
    public boolean capturesOn(TeleportCauseCategory cause) {
        Objects.requireNonNull(cause, "cause");
        return !ignoredCauses.contains(cause);
    }
}
