package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code playerwarps_*} / {@code playerwarp_*} placeholders. It is an
 * adapter over the player-warps context's {@code PlayerWarpRepository} and the {@code PlayerWarpQuota} reducer
 * wired during bootstrap; when the playerwarps module is disabled the seam is absent and the placeholders
 * degrade to the dash.
 *
 * <p>The count and limit are read for the requesting player as an <em>owner</em>. Every read is over the warps
 * they own, never another player's. The limit resolves through the same {@code uxmessentials.pwarp.limit.<n>}
 * quota reducer {@code /setpwarp} enforces, but the placeholder has no world in hand, so it resolves the
 * unscoped family (a {@code null} world); a negative {@link #limit(PlayerRef)} encodes "unlimited". The
 * {@link #list(PlayerRef)} read returns the owner's warps in stored creation order so the indexed and list
 * placeholders render deterministically.
 */
public interface PlayerwarpsPlaceholders {

    /** How many warps {@code who} currently owns (the {@code /setpwarp} limit check's count). */
    int count(PlayerRef who);

    /** {@code who}'s resolved player-warp limit, or a negative value when unlimited. */
    int limit(PlayerRef who);

    /** The warps {@code who} owns in stored creation order; empty when they own none. */
    List<PlayerWarpView> list(PlayerRef who);

    /** The view of the warp {@code who} owns under {@code name} when it exists, else empty. */
    Optional<PlayerWarpView> find(PlayerRef who, String name);

    /**
     * The placeholder-facing projection of one player-warp: the owner's name, the world it points into, its
     * block-rounded coordinates, and its visit count. Only the fields the {@code PlayerWarp} aggregate actually
     * carries are exposed, there is no price or category on a player-warp, and coordinates are block-rounded
     * because a placeholder shows a position, not a teleport target.
     *
     * @param name the warp's name within its owner's set
     * @param owner the name of the player who owns the warp
     * @param world the world name the warp points into
     * @param blockX the block x coordinate
     * @param blockY the block y coordinate
     * @param blockZ the block z coordinate
     * @param visits how many times the warp has been used
     */
    record PlayerWarpView(String name, String owner, String world, int blockX, int blockY, int blockZ, long visits) {

        public PlayerWarpView {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(world, "world");
        }
    }
}
