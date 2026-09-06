package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * The anti-corruption layer between Bukkit handles and the kernel's value objects. Every outbound
 * adapter that crosses the boundary maps through here, so the {@code Location}/{@code Position} and
 * {@code Player}/{@code PlayerRef} translations are defined once rather than copy-pasted per adapter.
 *
 * <p>The mappers are pure static functions with no Bukkit lookups of their own, a caller already
 * holding a live {@code Player} or {@code World} translates it here; resolving a handle from an id is
 * the lookup ports' job, not this class's.
 */
@NullMarked
public final class BukkitRefs {

    private BukkitRefs() {}

    /** A {@link PlayerRef} carrying the player's uuid and current name. */
    public static PlayerRef toRef(Player player) {
        Objects.requireNonNull(player, "player");
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** A {@link WorldRef} carrying the world's persistent uid and operator-facing name. */
    public static WorldRef toRef(World world) {
        Objects.requireNonNull(world, "world");
        return new WorldRef(world.getUID(), world.getName());
    }

    /** A {@link Position} mapping a Bukkit {@link Location}; the location must have a world. */
    public static Position toPosition(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "location world");
        return new Position(
                toRef(world),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    /** A Bukkit {@link Location} in {@code world} for {@code position}; the caller resolves the world. */
    public static Location toLocation(World world, Position position) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        return new Location(world, position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }
}
