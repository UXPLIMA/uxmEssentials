package com.uxplima.uxmessentials.survival.domain;

/**
 * An immutable integer block coordinate: the unit {@link ConnectedBlockSearch} floods over. It is deliberately
 * world-agnostic: an adapter that runs the search over one Bukkit world maps a broken block to {@code new
 * BlockPos(block.getX(), block.getY(), block.getZ())} and resolves each visited coordinate back to a block in that
 * same world, so the coordinate carries no world identity of its own.
 *
 * @param x the block x coordinate
 * @param y the block y coordinate
 * @param z the block z coordinate
 */
public record BlockPos(int x, int y, int z) {

    /** This coordinate offset by {@code (dx, dy, dz)}: the neighbour step the search walks. */
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }
}
