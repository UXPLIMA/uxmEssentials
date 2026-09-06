package com.uxplima.uxmessentials.poses.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.application.port.SeatHandle;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.event.PoseStarted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Starts a sitting pose: {@code /sit} in place or on a right-clicked block. It gates on the {@code features.sit}
 * switch, the {@link PoseRegionGate}, and the one-session-per-player invariant (a player already posing is turned
 * away rather than double-seated), then spawns and mounts the seat through the {@link SeatPort} and records the
 * {@link PoseSession} in the registry, publishing {@link PoseStarted}.
 *
 * <p>The {@code returnLocation} is captured up front: when {@code return-to-start} is on it is the player's live
 * standing position (so ending the pose puts them back exactly there), otherwise the seat itself. The seat handle
 * the port mints is stored on the session so {@code StopPose} removes precisely the seat this call spawned.
 */
public final class StartSit {

    private final PoseSessions sessions;
    private final SeatPort seats;
    private final PoseRegionGate regionGate;
    private final PlayerLocator locator;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final boolean sitEnabled;
    private final boolean returnToStart;
    private final PoseCooldown cooldown;

    public StartSit(
            PoseSessions sessions,
            SeatPort seats,
            PoseRegionGate regionGate,
            PlayerLocator locator,
            DomainEventPublisher events,
            Clock clock,
            boolean sitEnabled,
            boolean returnToStart,
            PoseCooldown cooldown) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.seats = Objects.requireNonNull(seats, "seats");
        this.regionGate = Objects.requireNonNull(regionGate, "regionGate");
        this.locator = Objects.requireNonNull(locator, "locator");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sitEnabled = sitEnabled;
        this.returnToStart = returnToStart;
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    /**
     * Seat {@code who} at {@code seat} facing {@code yaw}. {@code inPlace} records whether the seat is the player's
     * own feet ({@code /sit} on the spot) rather than a block they clicked; it does not change the seating, only
     * the captured return location's default. Returns the outcome the adapter renders feedback from.
     */
    public SitOutcome start(PlayerRef who, Position seat, float yaw, boolean inPlace) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(seat, "seat");
        if (!sitEnabled) {
            return SitOutcome.DISABLED;
        }
        if (!regionGate.canPose(who, seat, PoseType.SIT)) {
            return SitOutcome.DENIED_REGION;
        }
        if (sessions.isPosing(who)) {
            return SitOutcome.ALREADY_POSING;
        }
        if (cooldown.remaining(who).isPresent()) {
            return SitOutcome.ON_COOLDOWN;
        }
        seatAndRecord(who, seat, yaw, inPlace);
        cooldown.stamp(who);
        return SitOutcome.STARTED;
    }

    private void seatAndRecord(PlayerRef who, Position seat, float yaw, boolean inPlace) {
        SeatHandle handle = seats.spawnSeat(seat, yaw);
        seats.mount(who, handle);
        Position returnLocation = returnLocationFor(who, seat, inPlace);
        PoseSession session = new PoseSession(who, PoseType.SIT, returnLocation, handle.id(), null, clock.instant());
        sessions.start(session);
        events.publish(new PoseStarted(session));
    }

    /** Where ending the pose should return the player: their live position when returning is on, else the seat. */
    private Position returnLocationFor(PlayerRef who, Position seat, boolean inPlace) {
        if (!returnToStart || inPlace) {
            return seat;
        }
        return locator.locate(who).orElse(seat);
    }

    /** How a {@code /sit} attempt resolved, so the adapter can send the matching feedback. */
    public enum SitOutcome {

        /** The pose began; the seat is spawned and the player mounted. */
        STARTED,

        /** {@code features.sit} is off: sitting is not offered. */
        DISABLED,

        /** The region gate refused the pose here (a claim or WorldGuard veto). */
        DENIED_REGION,

        /** The player already holds a pose; the one-session invariant turns the second attempt away. */
        ALREADY_POSING,

        /** The player is still inside the pose cooldown window; the attempt is refused and nothing is stamped. */
        ON_COOLDOWN
    }
}
