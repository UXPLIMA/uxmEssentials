package com.uxplima.uxmessentials.regions.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import org.jspecify.annotations.NullMarked;

/**
 * Resolves the cuboid a player is defining for {@code /regions create}. Two sources feed it: a player's WorldEdit
 * selection (the wand the operator already knows), and, for a player without WorldEdit, or one who prefers commands
 *: two corners marked in-game with {@code /regions pos1} / {@code /regions pos2}. The command asks only for the
 * resolved {@link RegionBounds}, so it is agnostic to which source answered.
 *
 * <p>This is an inbound-adapter seam, not a core port: it speaks in the Bukkit {@link Player} the command already
 * holds. The production implementation reaches WorldEdit purely by reflection behind a plugin-present guard (the
 * same soft-dependency discipline the region reads use), and a test drives the marked-corner path with WorldEdit
 * absent.
 */
@NullMarked
public interface RegionSelection {

    /** Mark one corner of the manual selection at {@code player}'s current position. */
    void mark(Player player, Corner corner);

    /**
     * The cuboid {@code player} has defined: their WorldEdit selection when one is set, else the two corners they
     * marked (both present and in the same world), else empty when neither source yields a complete box.
     */
    Optional<RegionBounds> boundsFor(Player player);

    /** Which corner of the manual two-point selection a {@code /regions pos1|pos2} marks. */
    enum Corner {
        FIRST,
        SECOND
    }
}
