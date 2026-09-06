package com.uxplima.uxmessentials.poses.adapter.outbound;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.poses.application.port.SeatHandle;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link SeatPort} over a real, invisible seat entity. A small marker-free armour stand the player rides,
 * the battle-tested GSit mechanism. Every seat is spawned non-persistent (never written to disk) and PDC-tagged
 * with a once-created {@code uxmessentials:poses_seat} key, so the two ghost-prevention paths, {@link
 * #sweepOrphans()} on enable and {@link #removeSeatsIn(Chunk)} / {@link #removeSeatsIn(World)} on unload, can find
 * and reap any seat regardless of which run spawned it.
 *
 * <p>All entity work hops through the {@link Scheduler} so the seat and its rider stay on one region thread under
 * Folia (docs/02-concurrency): a seat is spawned and mounted on the region that owns its position (the caller
 * seats a player at a block within reach, so the rider is in that region too), and removed there. The live index
 * keyed by handle id carries each seat's position, filled in synchronously at spawn time so a mount scheduled
 * immediately after resolves the right region even before the spawn task has run.
 */
@NullMarked
public final class BukkitSeatPort implements SeatPort {

    /** The PDC key value every seat entity carries; created once (never on a hot path). */
    private static final String SEAT_KEY = "poses_seat";

    /**
     * The vertical nudge applied to the seat so a marker armour stand's rider lands on the block surface rather than
     * floating above it. A marker armour stand has a zero-size hitbox and a passenger attachment at its own origin, so
     * the rider sits at the seat's Y; GSit tunes that by +0.05 on modern servers (its {@code baseOffset} of -0.05 for
     * 1.20.2+) so the seated model's weight rests on the surface instead of sinking a hair into it. The seat position
     * the caller passes already carries the block's surface height (a full block's top, half a block for a slab or
     * stair), so this is the only mount-geometry constant the port owns.
     */
    private static final double SEAT_MOUNT_OFFSET = 0.05;

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final Logger log;
    private final NamespacedKey seatKey;
    private final ConcurrentHashMap<String, LiveSeat> live = new ConcurrentHashMap<>();

    public BukkitSeatPort(Plugin plugin, Scheduler scheduler, Logger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.seatKey = new NamespacedKey(plugin, SEAT_KEY);
    }

    @Override
    public SeatHandle spawnSeat(Position seat, float yaw) {
        Objects.requireNonNull(seat, "seat");
        String id = UUID.randomUUID().toString();
        LiveSeat entry = new LiveSeat(seat);
        live.put(id, entry);
        scheduler.onRegion(seat, () -> spawnEntity(id, entry, seat, yaw));
        return SeatHandle.of(id);
    }

    private void spawnEntity(String id, LiveSeat entry, Position seat, float yaw) {
        World world = plugin.getServer().getWorld(seat.world().uid());
        if (world == null) {
            live.remove(id);
            log.warn("poses seat skipped. World {} is not loaded", seat.world().name());
            return;
        }
        Location at = BukkitRefs.toLocation(world, seat);
        at.setYaw(yaw);
        // Raise the spawn so the marker seat's rider rests on the surface instead of floating a block up.
        at.setY(at.getY() + SEAT_MOUNT_OFFSET);
        entry.attach(world.spawn(at, ArmorStand.class, stand -> configureSeat(stand, id)));
    }

    /** Make the seat an invisible, weightless, non-colliding marker the rider sits on, and tag it non-persistent. */
    private void configureSeat(ArmorStand stand, String id) {
        stand.setInvisible(true);
        stand.setSmall(true);
        // A marker stand has a zero hitbox and seats its rider at its own Y (GSit's seat mechanism), so the rider sits
        // on the block instead of floating above it the way a full-bodied stand's ~1-block ride height would push them.
        stand.setMarker(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setGravity(false);
        stand.setAI(false);
        stand.setCollidable(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        // Never persisted: a crash or an ungraceful shutdown must not leave a seat behind on disk. An empty seat
        // that unloads because no player is near is harmless: the rider always keeps their own seat loaded.
        stand.setPersistent(false);
        stand.getPersistentDataContainer().set(seatKey, PersistentDataType.STRING, id);
    }

    @Override
    public void mount(PlayerRef rider, SeatHandle seat) {
        Objects.requireNonNull(rider, "rider");
        Objects.requireNonNull(seat, "seat");
        LiveSeat entry = live.get(seat.id());
        if (entry == null) {
            return;
        }
        scheduler.onRegion(entry.position(), () -> {
            Entity stand = entry.entity();
            Player player = plugin.getServer().getPlayer(rider.uuid());
            if (stand != null && stand.isValid() && player != null) {
                stand.addPassenger(player);
            }
        });
    }

    @Override
    public void mountOnPlayer(PlayerRef rider, PlayerRef target) {
        Objects.requireNonNull(rider, "rider");
        Objects.requireNonNull(target, "target");
        // No seat entity for a player-sit: mount straight onto the carrier on the carrier's own entity thread (Folia).
        // addPassenger chains, so a rider on a rider on a player just works.
        scheduler.onEntity(target, () -> {
            Player carrier = plugin.getServer().getPlayer(target.uuid());
            Player passenger = plugin.getServer().getPlayer(rider.uuid());
            if (carrier != null && carrier.isValid() && passenger != null) {
                carrier.addPassenger(passenger);
            }
        });
    }

    @Override
    public void removeSeat(SeatHandle seat) {
        Objects.requireNonNull(seat, "seat");
        LiveSeat entry = live.remove(seat.id());
        if (entry == null) {
            return;
        }
        scheduler.onRegion(entry.position(), () -> removeIfValid(entry.entity()));
    }

    @Override
    public void dismount(PlayerRef rider) {
        Objects.requireNonNull(rider, "rider");
        // Take the rider off whatever they ride, on the rider's own entity thread; a no-op if already off (Bukkit
        // drops a passenger when the carrier is removed, so a carrier-quit cleanup often finds nothing left to do).
        scheduler.onEntity(rider, () -> {
            Player player = plugin.getServer().getPlayer(rider.uuid());
            if (player != null && player.isInsideVehicle()) {
                player.leaveVehicle();
            }
        });
    }

    @Override
    public boolean isOccupied(Position seat) {
        Objects.requireNonNull(seat, "seat");
        return live.values().stream().anyMatch(entry -> entry.position().sameBlock(seat));
    }

    @Override
    public int sweepOrphans() {
        int[] removed = {0};
        scheduler.onGlobal(() -> {
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (isSeat(entity)) {
                        removed[0]++;
                        scheduler.onRegion(BukkitRefs.toPosition(entity.getLocation()), () -> removeIfValid(entity));
                    }
                }
            }
        });
        live.clear();
        return removed[0];
    }

    /** Remove every live seat now, the module-stop path, so a disable or reload leaves no seat behind. */
    public void removeAll() {
        for (LiveSeat entry : live.values()) {
            scheduler.onRegion(entry.position(), () -> removeIfValid(entry.entity()));
        }
        live.clear();
    }

    /** Remove the tagged seats in an unloading chunk; the unload event already runs on the owning region thread. */
    public void removeSeatsIn(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        removeTagged(Arrays.asList(chunk.getEntities()));
    }

    /** Remove the tagged seats in an unloading world; the unload event already runs on the owning thread. */
    public void removeSeatsIn(World world) {
        Objects.requireNonNull(world, "world");
        removeTagged(world.getEntities());
    }

    private void removeTagged(Iterable<Entity> entities) {
        for (Entity entity : entities) {
            if (isSeat(entity)) {
                forget(entity);
                removeIfValid(entity);
            }
        }
    }

    /** Drop the live entry the tag names, so a later {@link #removeSeat} for the same handle is a clean no-op. */
    private void forget(Entity entity) {
        String id = entity.getPersistentDataContainer().get(seatKey, PersistentDataType.STRING);
        if (id != null) {
            live.remove(id);
        }
    }

    private boolean isSeat(Entity entity) {
        return entity.getPersistentDataContainer().has(seatKey, PersistentDataType.STRING);
    }

    private static void removeIfValid(@Nullable Entity entity) {
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    /** One live seat: its position (known synchronously) plus the entity, attached once the spawn task runs. */
    private static final class LiveSeat {
        private final Position position;
        private volatile @Nullable Entity entity;

        LiveSeat(Position position) {
            this.position = position;
        }

        Position position() {
            return position;
        }

        @Nullable Entity entity() {
            return entity;
        }

        void attach(Entity entity) {
            this.entity = entity;
        }
    }
}
