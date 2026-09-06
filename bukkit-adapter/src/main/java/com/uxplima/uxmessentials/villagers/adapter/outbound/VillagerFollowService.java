package com.uxplima.uxmessentials.villagers.adapter.outbound;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.villagers.application.VillagersMessageKey;
import com.uxplima.uxmessentials.villagers.domain.FollowRange;
import org.jspecify.annotations.NullMarked;

/**
 * The follow runtime: a single repeating task on the {@link Scheduler} port that, every tick period, walks each
 * following villager toward its owner while the owner is in range. It owns no range rule of its own, the pure
 * {@link FollowRange} decides whether a villager should move, and delegates the actual pathfinding to the
 * {@link VillagerMover} seam.
 *
 * <p><b>State.</b> The villager→owner sessions ride a {@link ConcurrentHashMap} mutated only through {@code put} /
 * {@code remove}, and each session's owner is mirrored into the villager's PDC ({@link PdcVillagerFlags}) so the
 * pairing is durable and inspectable. {@code /villager follow} flips both through {@link #toggle}.
 *
 * <p><b>Threading (Folia).</b> The repeating task runs on the global region thread. The one thread that can read
 * the whole roster of villagers and their owners coherently, exactly like {@link VillagerRestockSweep}. There
 * {@link #tick} resolves each session's villager and owner by uuid, snapshots their owning-region positions, decides
 * move-or-stop, and hops the walk onto the villager's region via {@link Scheduler#onRegion}. It never reads or
 * mutates a foreign villager inline; the pathfinder touch runs on the villager's own region thread. On Paper every
 * region is the main thread, so the loop behaves like a straight inline pass.
 *
 * <p><b>Opt-in.</b> {@link #start()} schedules nothing and hands back a no-op closeable when the feature is disabled,
 * so a disabled runtime holds zero state; the wiring always calls {@code start()} and closes the returned handle on
 * module stop, which also drops every session.
 */
@NullMarked
public final class VillagerFollowService {

    /** How often the follow sweep re-targets each following villager (half a second, smooth without churn). */
    private static final Duration FOLLOW_PERIOD = Duration.ofMillis(500);

    private final Server server;
    private final Scheduler scheduler;
    private final PdcVillagerFlags flags;
    private final VillagerMover mover;
    private final FollowRange range;
    private final double speed;
    private final CommandFeedback feedback;
    private final boolean enabled;
    private final ConcurrentHashMap<UUID, UUID> sessions = new ConcurrentHashMap<>();

    public VillagerFollowService(
            Server server,
            Scheduler scheduler,
            PdcVillagerFlags flags,
            VillagerMover mover,
            FollowRange range,
            double speed,
            Messages messages,
            boolean enabled) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.mover = Objects.requireNonNull(mover, "mover");
        this.range = Objects.requireNonNull(range, "range");
        this.speed = speed;
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
        this.enabled = enabled;
    }

    /**
     * Start the repeating follow sweep, returning the handle the module closes on stop; closing it cancels the task
     * and drops every session. When the feature is disabled nothing is scheduled and a no-op closeable is returned.
     */
    public AutoCloseable start() {
        if (!enabled) {
            return () -> {};
        }
        AutoCloseable task = scheduler.repeatGlobal(this::tick, FOLLOW_PERIOD, FOLLOW_PERIOD);
        return () -> {
            try {
                task.close();
            } finally {
                sessions.clear();
            }
        };
    }

    /**
     * How many villagers are currently following {@code ownerId}. A dead or despawned villager is only dropped from
     * the map by the follow sweep, so a count taken between sweeps can be one high for at most half a second; that
     * is the same window the sweep itself works against and is invisible on a HUD.
     */
    public int followingCount(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        int following = 0;
        for (UUID owner : sessions.values()) {
            if (ownerId.equals(owner)) {
                following++;
            }
        }
        return following;
    }

    /** Whether {@code villagerId} is currently following someone. */
    public boolean isFollowing(UUID villagerId) {
        return sessions.containsKey(Objects.requireNonNull(villagerId, "villagerId"));
    }

    /**
     * Toggle whether {@code villager} follows {@code player}, scheduled on the villager's region (it sits within reach
     * of the commanding player, so its region is the player's). A first toggle starts the follow, a second stops it.
     */
    public void toggle(Player player, PlayerRef ref, Villager villager) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(villager, "villager");
        scheduler.onEntity(ref, () -> applyToggle(player, villager));
    }

    private void applyToggle(Player player, Villager villager) {
        if (flags.followOwner(villager) != null) {
            flags.clearFollowOwner(villager);
            sessions.remove(villager.getUniqueId());
            mover.stop(villager);
            sendIfOnline(player, VillagersMessageKey.VILLAGERS_FOLLOW_STOPPED);
            return;
        }
        flags.setFollowOwner(villager, player.getUniqueId());
        sessions.put(villager.getUniqueId(), player.getUniqueId());
        sendIfOnline(player, VillagersMessageKey.VILLAGERS_FOLLOW_STARTED);
    }

    /** One follow pass on the global region thread: for each session, decide move-or-stop and hop it to the region. */
    public void tick() {
        for (Map.Entry<UUID, UUID> session : sessions.entrySet()) {
            advanceSession(session.getKey(), session.getValue());
        }
    }

    private void advanceSession(UUID villagerId, UUID ownerId) {
        if (!(server.getEntity(villagerId) instanceof Villager villager) || !villager.isValid()) {
            sessions.remove(villagerId); // the villager despawned or died, drop the dead session
            return;
        }
        Player owner = server.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return; // the owner is offline; hold the villager where it is until they return
        }
        Location ownerLoc = owner.getLocation();
        if (ownerLoc == null) {
            return; // the owner is mid-removal and has no location; skip this tick
        }
        Location villagerLoc = villager.getLocation();
        World villagerWorld = villagerLoc.getWorld();
        boolean sameWorld = villagerWorld != null && villagerWorld.equals(ownerLoc.getWorld());
        boolean move = range.shouldMove(sameWorld, sameWorld ? villagerLoc.distanceSquared(ownerLoc) : 0.0);
        Position villagerPos = BukkitRefs.toPosition(villagerLoc);
        Position ownerPos = BukkitRefs.toPosition(ownerLoc);
        scheduler.onRegion(villagerPos, () -> walk(villagerId, ownerPos, move));
    }

    // On the villager's own region thread: pathfind it toward the owner snapshot, or stop it. Re-resolve the villager
    // so a despawn between the global snapshot and this hop is a no-op rather than a stale-handle touch.
    private void walk(UUID villagerId, Position ownerPos, boolean move) {
        if (!(server.getEntity(villagerId) instanceof Villager villager) || !villager.isValid()) {
            return;
        }
        if (!move) {
            mover.stop(villager);
            return;
        }
        World world = server.getWorld(ownerPos.world().uid());
        if (world == null) {
            return; // the owner's world unloaded between hops; skip this tick rather than throw
        }
        mover.moveTo(villager, BukkitRefs.toLocation(world, ownerPos), speed);
    }

    private void sendIfOnline(Player player, VillagersMessageKey key) {
        if (player.isOnline()) {
            feedback.send(player, key);
        }
    }
}
