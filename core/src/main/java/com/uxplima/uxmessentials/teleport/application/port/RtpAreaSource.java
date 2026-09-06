package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;

/**
 * Resolves a world's live {@link SafeSearchArea}: its centre, radius band, and world-border clamp. The adapter
 * reads the world border and the current (possibly {@code /settpr}-swapped) radii to build it, so the pure use
 * cases ({@code /rtp biome} in particular) can obtain the same area the pre-warmed queue refills against without
 * touching Bukkit or duplicating the border maths. A world with no valid RTP zone (no queue, or a radius that no
 * longer fits inside a shrunken border) resolves to empty.
 */
public interface RtpAreaSource {

    /** The live safe-search area for {@code world}, or empty when the world has no usable RTP zone. */
    Optional<SafeSearchArea> areaFor(WorldRef world);
}
