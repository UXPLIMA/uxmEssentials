package com.uxplima.uxmessentials.poses.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.poses.application.port.CrawlView;
import com.uxplima.uxmessentials.poses.application.port.PosePort;
import com.uxplima.uxmessentials.poses.application.port.PoseReturn;
import com.uxplima.uxmessentials.poses.application.port.SeatHandle;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.poses.application.port.Snores;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.event.PoseEnded;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Ends a player's pose. By their own command, or because they moved, took damage, dismounted, teleported, or
 * quit. It pulls the active {@link PoseSession} from the registry (a player who is not posing is a safe no-op),
 * removes the seat entity the session recorded so no ghost is left behind, clears any free-pose render and snore,
 * optionally returns the player to where the pose began, clears the session, and publishes {@link PoseEnded}.
 *
 * <p>The free-pose render and the snore loop are cleared through the {@link PosePort} and {@link Snores} on every
 * exit, unconditionally: a plain block-sit or a player-sit never applied either, so both calls are safe no-ops for
 * those sessions, which keeps this exit free of pose-type branching. What a session <em>rode</em> does branch,
 * though: a block/in-place sit rides a spawned seat entity (removed here), a player-sit rides a carrier (the rider
 * is dismounted), and a {@link PoseType#CRAWL} rides the swimming pose plus a client-only ceiling, both released
 * through the {@link CrawlView} so nothing is left holding the player down.
 *
 * <p>The {@code return-to-start} teleport is suppressed on the exits where it would be wrong: a quit (the player
 * is gone) and a teleport (returning them would fight the teleport that ended the pose) call
 * {@link #stop(PlayerRef, boolean)} with {@code allowReturn = false}. The command, sneak, damage, and dismount
 * exits use {@link #stop(PlayerRef)}, which returns the player when the server is configured to. A crawl is never
 * returned wherever it ends: a pose you walk around in has no meaningful start to snap back to.
 */
public final class StopPose {

    private final PoseSessions sessions;
    private final SeatPort seats;
    private final PosePort poses;
    private final Snores snores;
    private final CrawlView crawlView;
    private final PoseReturn poseReturn;
    private final DomainEventPublisher events;
    private final boolean returnToStart;

    public StopPose(
            PoseSessions sessions,
            SeatPort seats,
            PosePort poses,
            Snores snores,
            CrawlView crawlView,
            PoseReturn poseReturn,
            DomainEventPublisher events,
            boolean returnToStart) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.seats = Objects.requireNonNull(seats, "seats");
        this.poses = Objects.requireNonNull(poses, "poses");
        this.snores = Objects.requireNonNull(snores, "snores");
        this.crawlView = Objects.requireNonNull(crawlView, "crawlView");
        this.poseReturn = Objects.requireNonNull(poseReturn, "poseReturn");
        this.events = Objects.requireNonNull(events, "events");
        this.returnToStart = returnToStart;
    }

    /** End {@code who}'s pose, returning them to where it began when the server is configured to. */
    public Optional<PoseSession> stop(PlayerRef who) {
        return stop(who, true);
    }

    /**
     * End {@code who}'s pose. When {@code allowReturn} is false the {@code return-to-start} teleport is skipped
     * regardless of config, for the quit and teleport exits where returning the player would be wrong. Returns the
     * session that ended, or empty when the player was not posing.
     */
    public Optional<PoseSession> stop(PlayerRef who, boolean allowReturn) {
        Objects.requireNonNull(who, "who");
        Optional<PoseSession> ended = sessions.stop(who);
        if (ended.isEmpty()) {
            return Optional.empty();
        }
        PoseSession session = ended.get();
        if (session.type() == PoseType.CRAWL) {
            // A crawl rides no seat entity: it is held by the swimming pose and a client-only ceiling. Releasing
            // both is what stands the player back up, whatever the exit.
            crawlView.release(who);
        } else if (session.target() != null) {
            // A player-sit rides a carrier, not a seat entity: take the rider off the carrier directly.
            seats.dismount(who);
        } else {
            // A block/in-place sit rides a spawned seat: removing it dismounts the rider and leaves no ghost.
            seats.removeSeat(SeatHandle.of(session.seatHandle()));
        }
        // Undo any free-pose render (the lie-down body pose, a running spin, the crawl's swimming pose) and stop the
        // snore loop. Both are safe no-ops for a plain sit or player-sit, which never applied either, so no further
        // pose-type branch is needed here.
        poses.clearPose(who);
        snores.stopSnoring(who);
        // A crawl is never returned: it is a pose you walk around in, so there is no meaningful spot to snap back to.
        if (allowReturn && returnToStart && session.type() != PoseType.CRAWL) {
            poseReturn.returnTo(who, session.returnLocation());
        }
        events.publish(new PoseEnded(session));
        return ended;
    }
}
