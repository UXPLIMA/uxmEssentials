package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code warps_*} / {@code warp_*} placeholders. It is an adapter over
 * the warps context's {@code WarpRepository} and the {@code ListWarps} access filter (which folds the per-warp
 * {@code uxmessentials.warp.use.<warp>} node and a warp's optional extra permission), wired during bootstrap;
 * when the warps module is disabled the seam is absent and every placeholder degrades.
 *
 * <p>Visibility is respected throughout: {@link #count} and {@link #accessibleNames} count and list only the
 * warps the player may teleport to, and {@link #find} answers {@link Optional#empty()} for a warp the player
 * cannot reach (and for one that does not exist) so a placeholder never leaks a hidden warp's coordinates.
 */
public interface WarpsPlaceholders {

    /** How many warps {@code who} may use (the same set the {@code /warps} list shows them). */
    int count(PlayerRef who);

    /** The names of the warps {@code who} may use, in stored creation order. */
    List<String> accessibleNames(PlayerRef who);

    /** The view of the warp named {@code name} when it exists and {@code who} may use it, else empty. */
    Optional<WarpView> find(PlayerRef who, String name);

    /**
     * The placeholder-facing projection of one warp: the world it points into, its block-rounded coordinates,
     * its visit count, its owner's name, and its price ({@link BigDecimal#ZERO} for a free warp). Only the
     * fields the {@code Warp} aggregate actually carries are exposed. Coordinates are block-rounded because a
     * placeholder shows a position, not a teleport target.
     *
     * @param world the world name the warp points into
     * @param blockX the block x coordinate
     * @param blockY the block y coordinate
     * @param blockZ the block z coordinate
     * @param visits how many times the warp has been used
     * @param owner the name of the player who created the warp
     * @param cost the price to use the warp; {@link BigDecimal#ZERO} for a free warp
     */
    record WarpView(String world, int blockX, int blockY, int blockZ, long visits, String owner, BigDecimal cost) {

        public WarpView {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(cost, "cost");
        }
    }
}
