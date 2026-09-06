package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * The FOLLOW gadget's runtime: a single repeating task on the {@code Scheduler} port that, each tick, teleports
 * every following staff member onto the player they follow. This lives entirely in the adapter: there is no
 * core port, no DB row, because following is transient session work that vanishes with a restart, a quit, or a
 * mode exit.
 *
 * <p>The staff→target sessions ride a {@link ConcurrentHashMap} mutated only through {@code put}/{@code remove}.
 * A session auto-ends when either player goes offline, when the staff member has left staff mode, or when the
 * staff member can no longer see the target (the target vanished): the staff member is told through
 * {@link StaffMessageKey#STAFF_FOLLOW_ENDED} on their own entity thread and the session is dropped.
 *
 * <p>Following is continuous, so it must not route through the cooldown-gated teleport engine; instead each tick
 * does its own two region hops, mirroring {@code AsyncTeleportExecutor}'s region discipline (docs/02-concurrency
 * §6.5). The repeating task itself runs on the global region thread and does nothing but iterate the session map
 * and dispatch the hops. It never reads or moves an entity, since on Folia each player is owned by its own
 * region thread. For each session it first hops to the <em>target</em>'s entity thread to snapshot the target's
 * location, then hops to the <em>staff</em> member's entity thread to {@code teleportAsync} them onto that
 * snapshot. Each hop re-resolves the live {@code Player} (no stale handle crosses a thread) and treats an offline
 * player as a reason to end the session.
 */
@NullMarked
public final class StaffFollowService {

    private final Server server;
    private final Scheduler scheduler;
    private final Notifier notifier;
    private final Logger log;
    private final Predicate<UUID> inStaffMode;
    private final ConcurrentHashMap<UUID, PlayerRef> sessions = new ConcurrentHashMap<>();
    private final AutoCloseable task;

    public StaffFollowService(
            Server server,
            Scheduler scheduler,
            Notifier notifier,
            Logger log,
            Predicate<UUID> inStaffMode,
            int intervalTicks) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
        this.inStaffMode = Objects.requireNonNull(inStaffMode, "inStaffMode");
        Duration period = Duration.ofMillis(Math.max(1L, intervalTicks) * 50L);
        this.task = scheduler.repeatGlobal(this::tick, period, period);
    }

    /**
     * Start following {@code target} ({@code true} returned) or, if already following, stop ({@code false}). A
     * staff member follows at most one target at a time, so starting a new follow replaces any prior one. A staff
     * member cannot follow themselves; that request is rejected with {@code false} and starts nothing.
     */
    public boolean toggle(Player staff, Player target) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        UUID staffId = staff.getUniqueId();
        UUID targetId = target.getUniqueId();
        if (staffId.equals(targetId)) {
            return false;
        }
        PlayerRef current = sessions.get(staffId);
        if (current != null && targetId.equals(current.uuid())) {
            sessions.remove(staffId);
            return false;
        }
        sessions.put(staffId, new PlayerRef(targetId, target.getName()));
        return true;
    }

    /** Stop {@code staffId} following, if they were. */
    public void stop(UUID staffId) {
        sessions.remove(Objects.requireNonNull(staffId, "staffId"));
    }

    /** Whether {@code staffId} is currently following someone. */
    public boolean isFollowing(UUID staffId) {
        return sessions.containsKey(Objects.requireNonNull(staffId, "staffId"));
    }

    /** Cancel the repeating task and drop every session. Called on module stop. */
    public void shutdown() {
        try {
            task.close();
        } catch (Exception e) {
            // A cancel failure must not strand the caller (the wiring exits staff after this); log and carry on
            // so the session map is still cleared and the module disable completes.
            log.error("failed to cancel the staff follow task", e);
        }
        sessions.clear();
    }

    /** One pass over the live sessions; visible so a test can drive a single tick deterministically. */
    public void tick() {
        for (Map.Entry<UUID, PlayerRef> session : sessions.entrySet()) {
            advance(session.getKey(), session.getValue());
        }
    }

    // Two region hops per session: read the target's location on the target's thread, then teleport the staff on
    // the staff's thread, so neither a foreign entity read nor the teleport ever runs off its owning region.
    private void advance(UUID staffId, PlayerRef targetRef) {
        scheduler.onEntity(targetRef, () -> snapshotTarget(staffId, targetRef), () -> end(staffId));
    }

    private void snapshotTarget(UUID staffId, PlayerRef targetRef) {
        Player target = server.getPlayer(targetRef.uuid());
        if (target == null || !target.isOnline()) {
            end(staffId);
            return;
        }
        // Snapshot to a plain Position rather than carrying a live Location across the region hop; the staff
        // thread rebuilds the Location from it, the same value-not-handle handoff AsyncTeleportExecutor uses.
        Position snapshot = BukkitRefs.toPosition(Objects.requireNonNull(target.getLocation(), "target location"));
        PlayerRef staffRef = scheduleRef(staffId);
        scheduler.onEntity(staffRef, () -> moveStaff(staffId, targetRef, snapshot), () -> end(staffId));
    }

    private void moveStaff(UUID staffId, PlayerRef targetRef, Position snapshot) {
        Player staff = server.getPlayer(staffId);
        if (staff == null || !staff.isOnline() || !inStaffMode.test(staffId)) {
            end(staffId);
            return;
        }
        Player target = server.getPlayer(targetRef.uuid());
        if (target == null || !staff.canSee(target)) {
            end(staffId);
            return;
        }
        World world = server.getWorld(snapshot.world().uid());
        if (world == null) {
            return; // the target's world unloaded between hops; skip this tick rather than end the session
        }
        var ignored = staff.teleportAsync(BukkitRefs.toLocation(world, snapshot));
    }

    /** A ref carrying only the uuid, for scheduling onto an entity thread where the name is never read. */
    private static PlayerRef scheduleRef(UUID uuid) {
        return new PlayerRef(uuid, uuid.toString());
    }

    // Drop the session and tell the staff member it ended, on their own entity thread; the onEntity hop
    // silently no-ops if they are offline, so an end triggered from the target's thread still reaches a live
    // staff member without ever touching their entity from the wrong region.
    private void end(UUID staffId) {
        sessions.remove(staffId);
        scheduler.onEntity(scheduleRef(staffId), () -> notifyEnded(staffId));
    }

    private void notifyEnded(UUID staffId) {
        Player staff = server.getPlayer(staffId);
        if (staff != null && staff.isOnline()) {
            notifier.send(new PlayerRef(staff.getUniqueId(), staff.getName()), StaffMessageKey.STAFF_FOLLOW_ENDED);
        }
    }
}
