package com.uxplima.uxmessentials.poses.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound port over the invisible seat entity a sitting pose rides on. The adapter spawns a non-persistent,
 * PDC-tagged marker at the seat position, mounts the rider on it, and removes it when the pose ends, all through
 * the Folia-aware scheduler so the seat and its rider stay on one region thread.
 *
 * <p>The ghost-prevention contract lives here: every seat the adapter spawns is tagged and non-persistent, and
 * {@link #sweepOrphans()} reaps any tagged seat left behind by a crash or an ungraceful shutdown. A caller runs
 * the sweep on enable (before any pose starts) so a restart never inherits a stale seat.
 */
public interface SeatPort {

    /**
     * Spawn an invisible seat entity at {@code seat} facing {@code yaw} and return its handle. The rider is not
     * mounted yet: the caller follows with {@link #mount(PlayerRef, SeatHandle)}.
     */
    SeatHandle spawnSeat(Position seat, float yaw);

    /** Seat {@code rider} on the seat identified by {@code seat}; a no-op if the seat is already gone. */
    void mount(PlayerRef rider, SeatHandle seat);

    /**
     * Seat {@code rider} directly on {@code target}: the player-sit stacking pose, which spawns no seat entity.
     * The adapter runs {@code target.addPassenger(rider)} on the carrier's own region thread; because
     * {@code addPassenger} chains, a rider on a rider on a player just works. A no-op if either player is offline.
     */
    void mountOnPlayer(PlayerRef rider, PlayerRef target);

    /** Remove the seat identified by {@code seat}, dismounting whoever rides it; a no-op if already removed. */
    void removeSeat(SeatHandle seat);

    /**
     * Dismount {@code rider} from whatever they ride. The player-sit end path, where there is no seat entity to
     * remove, so the rider is taken off their carrier directly. A no-op when the rider is offline or not riding.
     */
    void dismount(PlayerRef rider);

    /** Whether a live seat already sits on {@code seat}'s block, so a second player cannot claim it. */
    boolean isOccupied(Position seat);

    /**
     * Remove every tagged seat entity across the loaded worlds and report how many were removed. Called on enable
     * to reap seats orphaned by a crash, so no pose ever inherits a ghost from a previous run.
     */
    int sweepOrphans();
}
