package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code homes_*} placeholders. It is an adapter over the homes
 * context's existing read ports ({@code HomeRepository} and the {@code HomeQuota} reducer) wired during
 * bootstrap; when the homes module is disabled the seam is absent and the placeholders degrade.
 *
 * <p>The count and limit are read for a specific player. The limit resolves through the same world-scoped
 * quota reducer {@code /sethome} uses, but the placeholder has no world in hand, so it resolves the
 * unscoped family (a {@code null} world), the value an operator sees on a hub or a non-world-scoped
 * server. A negative {@code limit()} encodes "unlimited". The {@link #list(PlayerRef)} read returns the
 * player's homes in their stable slot order so the indexed and list placeholders render deterministically.
 */
public interface HomesPlaceholders {

    /** How many homes {@code who} currently holds. */
    int count(PlayerRef who);

    /** {@code who}'s resolved home limit, or a negative value when unlimited. */
    int limit(PlayerRef who);

    /** {@code who}'s homes in slot-ascending order; empty when they hold none. */
    List<HomeView> list(PlayerRef who);

    /**
     * The placeholder-facing projection of one home: the display name (the player's label when set, else the
     * 1-based slot number), the world name, and the block-rounded coordinates. Coordinates are block-rounded
     * because a placeholder shows a position, not a teleport target. The sub-block precision is noise on a
     * scoreboard or in chat.
     *
     * @param name the home's display name
     * @param world the world name the home points into
     * @param blockX the block x coordinate
     * @param blockY the block y coordinate
     * @param blockZ the block z coordinate
     */
    record HomeView(String name, String world, int blockX, int blockY, int blockZ) {

        public HomeView {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(world, "world");
        }
    }
}
