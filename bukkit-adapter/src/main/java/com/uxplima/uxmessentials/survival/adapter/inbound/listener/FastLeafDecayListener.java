package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig.LeafDecay;
import com.uxplima.uxmessentials.survival.domain.BlockPos;
import com.uxplima.uxmessentials.survival.domain.LeafDecaySweep;
import org.jspecify.annotations.NullMarked;

/**
 * Fast-leaf-decay: when a log is broken, the non-persistent leaves within {@code radius} that a standing trunk no
 * longer supports are decayed a short delay later, rather than waiting for vanilla's slow random-tick sweep. It
 * listens on {@code BlockBreakEvent} (ignoring an already-cancelled break) for a log block and schedules the sweep;
 * the {@code breakNaturally} calls the harvesting cascade makes do not fire {@code BlockBreakEvent}, so felling a
 * whole tree still schedules exactly one sweep from the player's own break.
 *
 * <p>The which-leaves decision is the pure {@link LeafDecaySweep}: this listener only supplies the two predicates that
 * resolve a coordinate to a live block and read its type tag and (for a leaf) its persistent flag. A player-placed
 * (persistent) leaf is never decayed, and a leaf still within the support distance of a remaining log survives, so
 * breaking one log of a tree whose trunk still stands does not strip the leaves that trunk holds.
 *
 * <h2>Folia</h2>
 * The sweep mutates blocks, so it must run on the broken log's region. When a delay is configured the listener waits
 * off-thread through {@link Scheduler#asyncAfter} and then hops to the region with {@link Scheduler#onRegion} to do
 * the block work; with no delay it hops straight to the region. The off-thread step only schedules: it touches no
 * Bukkit state, and every block read or break happens inside the region task.
 */
@NullMarked
public final class FastLeafDecayListener implements Listener {

    /** Vanilla keeps a leaf alive while a log sits within six blocks of it; farther leaves decay. */
    private static final int SUPPORT_DISTANCE = 6;

    /** A safety cap on how many leaves one break may decay, so a giant canopy cannot stall the region. */
    private static final int MAX_LEAVES = 512;

    private final LeafDecay config;
    private final Scheduler scheduler;
    private final LeafDecaySweep sweep;

    public FastLeafDecayListener(LeafDecay config, Scheduler scheduler) {
        this.config = Objects.requireNonNull(config, "config");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sweep = new LeafDecaySweep(config.radius(), SUPPORT_DISTANCE, MAX_LEAVES);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block origin = event.getBlock();
        if (Tag.LOGS.isTagged(origin.getType())) {
            scheduleSweep(origin);
        }
    }

    /** Schedule the leaf sweep around {@code origin}, on its region, after the configured tick delay. */
    private void scheduleSweep(Block origin) {
        World world = origin.getWorld();
        BlockPos start = new BlockPos(origin.getX(), origin.getY(), origin.getZ());
        Position region = BukkitRefs.toPosition(origin.getLocation());
        Runnable task = () -> decay(world, start);
        if (config.delayTicks() <= 0) {
            scheduler.onRegion(region, task);
        } else {
            Duration delay = Duration.ofMillis(config.delayTicks() * 50L);
            scheduler.asyncAfter(delay, () -> scheduler.onRegion(region, task));
        }
    }

    /** Run the pure sweep over the live world and break every unsupported leaf it returns. */
    private void decay(World world, BlockPos origin) {
        List<BlockPos> leaves = sweep.unsupportedLeaves(
                origin,
                pos -> Tag.LOGS.isTagged(
                        world.getBlockAt(pos.x(), pos.y(), pos.z()).getType()),
                pos -> isDecayableLeaf(world.getBlockAt(pos.x(), pos.y(), pos.z())));
        for (BlockPos pos : leaves) {
            world.getBlockAt(pos.x(), pos.y(), pos.z()).breakNaturally();
        }
    }

    /** Whether {@code block} is a leaf that may decay: a leaves block that is not player-placed (persistent). */
    private static boolean isDecayableLeaf(Block block) {
        return Tag.LEAVES.isTagged(block.getType())
                && block.getBlockData() instanceof Leaves leaf
                && !leaf.isPersistent();
    }
}
