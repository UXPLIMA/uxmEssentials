package com.uxplima.uxmessentials.moderation.application.port;

import java.util.Objects;
import java.util.Optional;

/**
 * Read seam that resolves a named jail to the coordinates it sits at, merging the two jail sources the
 * {@link Sanctions} adapter already teleports against: the DB-backed {@link JailLocationStore} an operator fills
 * with {@code /setjail} (store-first), falling back to the read-only config jails. It is the display complement
 * to {@code sendToJail}, where that hops a player to the jail, this hands back the world and block coordinates
 * a management GUI shows in a jail's lore so an operator can see where a jail is without going there.
 *
 * <p>The adapter resolves both sources and rounds to block coordinates; a jail whose world is unresolved (a
 * config jail with no world, or a stored jail in an unloaded world) returns empty, exactly as the teleport path
 * no-ops on the same case. The implementation lives in the bukkit adapter; the GUI depends only on this contract.
 */
public interface JailLocator {

    /** The block coordinates of the named jail, or empty when the jail is unknown or its world is unresolved. */
    Optional<JailCoords> locate(String jail);

    /**
     * A jail's display coordinates: the operator-facing world name and the integer block position. This carries
     * only what a lore line shows, not a full domain {@code Position}, because a config jail has no persistent
     * world id to build one from.
     *
     * @param world the operator-facing world name
     * @param x the block x coordinate
     * @param y the block y coordinate
     * @param z the block z coordinate
     */
    record JailCoords(String world, int x, int y, int z) {
        public JailCoords {
            Objects.requireNonNull(world, "world");
        }
    }
}
