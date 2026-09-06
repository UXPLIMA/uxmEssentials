package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.application.port.NearbyPlayers;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NearbyPlayers} implementation for {@code /near}: the players within a radius of the viewer in the
 * same world, ordered nearest-first.
 *
 * <p>Scanning every other player's {@link Player#getLocation()} from the viewer's region thread is a torn read on
 * Folia: each player's position is owned by that player's own region. The whole scan therefore runs on the
 * global region thread (where the entire roster is consistently readable): it reads the viewer's live location,
 * snapshots every candidate's position, filters by radius, sorts nearest-first, and hands the result to the
 * supplied callback. The flow is push-shaped, not request-reply: the viewer's region thread schedules the scan
 * and returns immediately, never blocking on the global read. {@code onResolved} runs on the global thread, so
 * it must only forward the result to a sink that re-targets each delivery to the recipient's own thread.
 */
@NullMarked
public final class BukkitNearbyPlayers implements NearbyPlayers {

    private final Scheduler scheduler;

    public BukkitNearbyPlayers(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void within(PlayerRef viewer, int radius, Consumer<List<Nearby>> onResolved) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(onResolved, "onResolved");
        scheduler.onGlobal(() -> onResolved.accept(scan(viewer.uuid(), radius)));
    }

    /**
     * Compute the nearby list for {@code viewerId} at {@code radius}. Runs on the global region thread, where the
     * viewer's location and every candidate's location are all readable without tearing. An offline viewer yields
     * an empty list.
     */
    private static List<Nearby> scan(UUID viewerId, int radius) {
        Player self = Bukkit.getPlayer(viewerId);
        if (self == null || !self.isOnline()) {
            return List.of();
        }
        World world = self.getWorld();
        // Paper marks Player#getLocation() nullable (null only for an entity with no world, which a
        // connected player always has); guard it at the boundary.
        Location origin = Objects.requireNonNull(self.getLocation(), "viewer location");
        double radiusSquared = (double) radius * radius;
        return readPositions(viewerId, world.getUID()).stream()
                .flatMap(candidate -> measure(origin, candidate).stream())
                .filter(measured -> measured.squared() <= radiusSquared)
                .sorted(Comparator.comparingDouble(Measured::squared))
                .map(Measured::toNearby)
                .toList();
    }

    private static List<Located> readPositions(UUID viewerId, UUID worldId) {
        List<Located> located = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(viewerId)) {
                continue;
            }
            Location location = other.getLocation();
            if (location == null
                    || !worldId.equals(
                            location.getWorld() == null
                                    ? null
                                    : location.getWorld().getUID())) {
                continue;
            }
            located.add(new Located(BukkitRefs.toRef(other), location.clone()));
        }
        return located;
    }

    private static Optional<Measured> measure(Location origin, Located candidate) {
        if (!Objects.equals(candidate.location().getWorld(), origin.getWorld())) {
            return Optional.empty();
        }
        return Optional.of(new Measured(candidate.who(), candidate.location().distanceSquared(origin)));
    }

    /** A candidate's ref carried with a snapshot of its location, read once on the global thread. */
    private record Located(PlayerRef who, Location location) {}

    /** A nearby candidate carried with its squared distance so the filter and sort avoid repeated sqrt. */
    private record Measured(PlayerRef who, double squared) {

        Nearby toNearby() {
            return new Nearby(who, (int) Math.round(Math.sqrt(squared)));
        }
    }
}
