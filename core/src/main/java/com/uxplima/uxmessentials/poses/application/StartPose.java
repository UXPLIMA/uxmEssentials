package com.uxplima.uxmessentials.poses.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.poses.application.port.PosePort;
import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.application.port.SeatHandle;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.poses.application.port.Snores;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.event.PoseStarted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Starts a free pose in place, {@code /lay}, {@code /bellyflop}, or {@code /spin}. Like {@link StartSit} it gates on
 * the matching {@code features} switch, the {@link PoseRegionGate}, and the one-session-per-player invariant, then
 * anchors the player on a seat through the {@link SeatPort} so they cannot walk out of the pose, renders the pose
 * through the {@link PosePort}, records the {@link PoseSession}, and publishes {@link PoseStarted}.
 *
 * <p>A free pose is always struck where the player stands, so the seat position doubles as the return location, a
 * {@code return-to-start} server puts the player back exactly there when the pose ends. Only {@code /lay} snores: a
 * laying player is added to the {@link Snores} loop when the {@code snore} config is on, so a lie-down reads as
 * sleeping to those nearby; the other two poses never snore.
 */
public final class StartPose {

    private final PoseSessions sessions;
    private final SeatPort seats;
    private final PoseRegionGate regionGate;
    private final PosePort poses;
    private final Snores snores;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final boolean layEnabled;
    private final boolean bellyflopEnabled;
    private final boolean spinEnabled;
    private final boolean snoreEnabled;
    private final PoseCooldown cooldown;

    public StartPose(
            PoseSessions sessions,
            SeatPort seats,
            PoseRegionGate regionGate,
            PosePort poses,
            Snores snores,
            DomainEventPublisher events,
            Clock clock,
            boolean layEnabled,
            boolean bellyflopEnabled,
            boolean spinEnabled,
            boolean snoreEnabled,
            PoseCooldown cooldown) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.seats = Objects.requireNonNull(seats, "seats");
        this.regionGate = Objects.requireNonNull(regionGate, "regionGate");
        this.poses = Objects.requireNonNull(poses, "poses");
        this.snores = Objects.requireNonNull(snores, "snores");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.layEnabled = layEnabled;
        this.bellyflopEnabled = bellyflopEnabled;
        this.spinEnabled = spinEnabled;
        this.snoreEnabled = snoreEnabled;
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    /**
     * Pose {@code who} in {@code pose} at {@code at} facing {@code yaw}. The player's own feet, since a free pose is
     * always struck on the spot. {@code pose} must be one of {@link PoseType#LAY}, {@link PoseType#BELLYFLOP}, or
     * {@link PoseType#SPIN}; the sit poses have their own use cases. Returns the outcome the adapter renders feedback
     * from.
     */
    public PoseOutcome start(PlayerRef who, PoseType pose, Position at, float yaw) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(at, "at");
        if (!featureEnabled(pose)) {
            return PoseOutcome.DISABLED;
        }
        if (!regionGate.canPose(who, at, pose)) {
            return PoseOutcome.DENIED_REGION;
        }
        if (sessions.isPosing(who)) {
            return PoseOutcome.ALREADY_POSING;
        }
        if (cooldown.remaining(who).isPresent()) {
            return PoseOutcome.ON_COOLDOWN;
        }
        anchorAndRecord(who, pose, at, yaw);
        cooldown.stamp(who);
        return PoseOutcome.STARTED;
    }

    private void anchorAndRecord(PlayerRef who, PoseType pose, Position at, float yaw) {
        SeatHandle handle = seats.spawnSeat(at, yaw);
        seats.mount(who, handle);
        poses.applyPose(who, pose);
        if (pose == PoseType.LAY && snoreEnabled) {
            snores.startSnoring(who);
        }
        // The seat is where the player already stood, so it is also the return-to-start location.
        PoseSession session = new PoseSession(who, pose, at, handle.id(), null, clock.instant());
        sessions.start(session);
        events.publish(new PoseStarted(session));
    }

    /** Whether the {@code features} switch guarding {@code pose} is on. Rejects any pose this use case does not own. */
    private boolean featureEnabled(PoseType pose) {
        return switch (pose) {
            case LAY -> layEnabled;
            case BELLYFLOP -> bellyflopEnabled;
            case SPIN -> spinEnabled;
            default -> throw new IllegalArgumentException("StartPose handles only the free poses, not " + pose);
        };
    }

    /** How a free-pose attempt resolved, so the adapter can send the matching feedback. */
    public enum PoseOutcome {

        /** The pose began; the player is anchored and the pose rendered. */
        STARTED,

        /** The matching {@code features} switch is off: this pose is not offered. */
        DISABLED,

        /** The region gate refused the pose here (a claim or WorldGuard veto). */
        DENIED_REGION,

        /** The player already holds a pose; the one-session invariant turns the second attempt away. */
        ALREADY_POSING,

        /** The player is still inside the pose cooldown window; the attempt is refused and nothing is stamped. */
        ON_COOLDOWN
    }
}
