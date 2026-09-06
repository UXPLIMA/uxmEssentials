package com.uxplima.uxmessentials.poses.adapter.inbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import com.uxplima.uxmessentials.poses.domain.SittableBlocks;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Turns a Bukkit {@code Block} into the seat a player sits on, keeping the sit <em>policy</em> in the domain: the
 * {@link SittableBlocks} value decides whether the material is sittable, how high the seat rests, and whether the
 * block's facing applies. This adapter only reads the live block (its centre, its {@code BlockData} facing) and
 * folds the policy's answers into a domain {@link Position}. Shared by {@code /sit} (the block you look at) and the
 * right-click-to-sit listener so both seat a player identically.
 */
public final class SeatGeometry {

    private SeatGeometry() {}

    /** The resolved seat: the centred position (carrying the facing yaw) the seat entity spawns at. */
    public record SeatTarget(Position position, float yaw) {
        public SeatTarget {
            Objects.requireNonNull(position, "position");
        }
    }

    /**
     * The seat for {@code block}, or empty when the material is not sittable. The seat sits at the block's
     * horizontal centre, raised by the policy's offset; its yaw is the block's facing for a directional block
     * (a stair), otherwise {@code fallbackYaw} (the player's own look).
     */
    public static Optional<SeatTarget> forBlock(Block block, SittableBlocks policy, float fallbackYaw) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(policy, "policy");
        String material = block.getType().name();
        if (!policy.isSittable(material)) {
            return Optional.empty();
        }
        float yaw = policy.facingApplies(material) ? facingYaw(block, fallbackYaw) : fallbackYaw;
        Location base = block.getLocation();
        Position seat = new Position(
                BukkitRefs.toRef(block.getWorld()),
                base.getBlockX() + 0.5,
                base.getBlockY() + policy.seatOffset(material),
                base.getBlockZ() + 0.5,
                yaw,
                0f);
        return Optional.of(new SeatTarget(seat, yaw));
    }

    private static float facingYaw(Block block, float fallbackYaw) {
        BlockData data = block.getBlockData();
        return data instanceof Directional directional ? yawOf(directional.getFacing()) : fallbackYaw;
    }

    /** The Bukkit yaw a player faces when the seat adopts {@code face} (south is 0°, turning clockwise). */
    private static float yawOf(BlockFace face) {
        return switch (face) {
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }
}
