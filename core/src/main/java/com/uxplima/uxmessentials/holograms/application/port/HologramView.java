package com.uxplima.uxmessentials.holograms.application.port;

import java.util.UUID;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;

/**
 * Outbound port the use cases drive to keep the in-world rendering in step with the stored model. A
 * hologram is a native display entity, so creating, editing, moving, or deleting one must also spawn,
 * re-render, move, or despawn the live entity, but that is a Bukkit concern, so the use cases express the
 * intent through this narrow port and the bukkit adapter realises it on the right region thread (Folia)
 * over the uxmLib hologram API.
 *
 * <p>The port is fire-and-forget: every method returns {@code void}. A render call carries the full
 * {@link Hologram} snapshot so the adapter never re-queries; a despawn carries only the name.
 */
public interface HologramView {

    /** Spawn or replace the live entity for {@code hologram} to match the given snapshot. */
    void render(Hologram hologram);

    /** Despawn the live entity for the hologram under {@code name}; a no-op when none is shown. */
    void despawn(HologramName name);

    /**
     * Apply a single MANUAL-visibility viewer change to the live entity at once: show the hologram under
     * {@code name} to {@code viewer} when {@code visible}, hide it otherwise, so {@code /hologram show|hide}
     * takes effect without waiting for a refresh tick. A no-op when the hologram is not currently shown or the
     * viewer is offline; only meaningful while the hologram is in MANUAL mode.
     */
    void applyManualViewer(HologramName name, UUID viewer, boolean visible);
}
