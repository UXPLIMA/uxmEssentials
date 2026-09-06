package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.poses.application.StartPlayerSit.PlayerSitOutcome;
import com.uxplima.uxmessentials.poses.application.port.PlayerSitPreferences;
import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.application.port.SeatHandle;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.event.PoseStarted;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link StartPlayerSit}: the five refusals ({@code features.player-sit} off, sitting on yourself, being
 * already posed, a target who refuses, and the region gate) leave no mount and no session, and a success mounts the
 * rider onto the carrier and records a {@link PoseType#PLAYER_SIT} session carrying the target, publishing
 * {@link PoseStarted}. All ports are fakes, no Bukkit.
 */
class StartPlayerSitTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef RIDER = new PlayerRef(UUID.randomUUID(), "Rider");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "Target");
    private static final Position RIDER_STANDING = new Position(WORLD, 11.0, 64.0, 21.0, 0f, 0f);

    private final PoseSessions sessions = new PoseSessions();
    private final RecordingSeatPort seats = new RecordingSeatPort();
    private final FakePlayerSitPreferences preferences = new FakePlayerSitPreferences();
    private final RecordingEvents events = new RecordingEvents();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void deniesWhenThePlayerSitFeatureIsDisabled() {
        StartPlayerSit startPlayerSit = newStartPlayerSit(false);

        assertThat(startPlayerSit.start(RIDER, TARGET)).isEqualTo(PlayerSitOutcome.DISABLED);
        assertThat(seats.mountedOn).isEmpty();
        assertThat(sessions.isPosing(RIDER)).isFalse();
        assertThat(events.published).isEmpty();
    }

    @Test
    void deniesWhenTheRiderTriesToSitOnThemselves() {
        StartPlayerSit startPlayerSit = newStartPlayerSit(true);

        assertThat(startPlayerSit.start(RIDER, RIDER)).isEqualTo(PlayerSitOutcome.SELF);
        assertThat(seats.mountedOn).isEmpty();
        assertThat(sessions.isPosing(RIDER)).isFalse();
    }

    @Test
    void deniesWhenTheRiderIsAlreadyPosing() {
        sessions.start(new PoseSession(RIDER, PoseType.SIT, RIDER_STANDING, "existing", null, clock.instant()));
        StartPlayerSit startPlayerSit = newStartPlayerSit(true);

        assertThat(startPlayerSit.start(RIDER, TARGET)).isEqualTo(PlayerSitOutcome.ALREADY_POSING);
        assertThat(seats.mountedOn).isEmpty();
    }

    @Test
    void deniesWhenTheTargetRefusesToBeSatOn() {
        preferences.allowing.put(TARGET.uuid(), false);
        StartPlayerSit startPlayerSit = newStartPlayerSit(true);

        assertThat(startPlayerSit.start(RIDER, TARGET)).isEqualTo(PlayerSitOutcome.TARGET_REFUSES);
        assertThat(seats.mountedOn).isEmpty();
        assertThat(sessions.isPosing(RIDER)).isFalse();
        assertThat(events.published).isEmpty();
    }

    @Test
    void deniesWhenTheRegionGateRefuses() {
        StartPlayerSit startPlayerSit = new StartPlayerSit(
                sessions,
                seats,
                preferences,
                (who, where, type) -> false,
                who -> Optional.of(RIDER_STANDING),
                events,
                clock,
                true,
                PoseCooldown.unlimited());

        assertThat(startPlayerSit.start(RIDER, TARGET)).isEqualTo(PlayerSitOutcome.DENIED_REGION);
        assertThat(seats.mountedOn).isEmpty();
        assertThat(sessions.isPosing(RIDER)).isFalse();
        assertThat(events.published).isEmpty();
    }

    @Test
    void gatesThePlayerSitWithThePlayerSitPoseType() {
        List<PoseType> consulted = new ArrayList<>();
        PoseRegionGate recording = (who, where, type) -> {
            consulted.add(type);
            return true;
        };
        StartPlayerSit startPlayerSit = new StartPlayerSit(
                sessions,
                seats,
                preferences,
                recording,
                who -> Optional.of(RIDER_STANDING),
                events,
                clock,
                true,
                PoseCooldown.unlimited());

        startPlayerSit.start(RIDER, TARGET);

        assertThat(consulted).containsExactly(PoseType.PLAYER_SIT);
    }

    @Test
    void mountsTheRiderOntoTheTargetAndRecordsAPlayerSitSession() {
        StartPlayerSit startPlayerSit = newStartPlayerSit(true);

        assertThat(startPlayerSit.start(RIDER, TARGET)).isEqualTo(PlayerSitOutcome.STARTED);

        assertThat(seats.mountedOn).containsExactly(new Mount(RIDER, TARGET));
        PoseSession session = sessions.current(RIDER).orElseThrow();
        assertThat(session.type()).isEqualTo(PoseType.PLAYER_SIT);
        assertThat(session.target()).isEqualTo(TARGET);
        // The return location is captured from the rider's live standing position, so standing up returns them there.
        assertThat(session.returnLocation()).isEqualTo(RIDER_STANDING);
        assertThat(events.published).singleElement().isInstanceOf(PoseStarted.class);
    }

    private StartPlayerSit newStartPlayerSit(boolean playerSitEnabled) {
        return new StartPlayerSit(
                sessions,
                seats,
                preferences,
                (who, where, type) -> true,
                who -> Optional.of(RIDER_STANDING),
                events,
                clock,
                playerSitEnabled,
                PoseCooldown.unlimited());
    }

    private record Mount(PlayerRef rider, PlayerRef target) {}

    /** Records the (rider, target) pairs the port was asked to mount; the seat-entity paths are unused here. */
    private static final class RecordingSeatPort implements SeatPort {
        private final List<Mount> mountedOn = new ArrayList<>();

        @Override
        public SeatHandle spawnSeat(Position seat, float yaw) {
            return SeatHandle.of("unused");
        }

        @Override
        public void mount(PlayerRef rider, SeatHandle seat) {}

        @Override
        public void mountOnPlayer(PlayerRef rider, PlayerRef target) {
            mountedOn.add(new Mount(rider, target));
        }

        @Override
        public void dismount(PlayerRef rider) {}

        @Override
        public void removeSeat(SeatHandle seat) {}

        @Override
        public boolean isOccupied(Position seat) {
            return false;
        }

        @Override
        public int sweepOrphans() {
            return 0;
        }
    }

    /** A per-player preference map; an unset player allows sitting (the GSit default). */
    private static final class FakePlayerSitPreferences implements PlayerSitPreferences {
        private final java.util.Map<UUID, Boolean> allowing = new java.util.HashMap<>();

        @Override
        public boolean allowsSitting(PlayerRef who) {
            return allowing.getOrDefault(who.uuid(), true);
        }

        @Override
        public boolean toggle(PlayerRef who) {
            boolean now = !allowsSitting(who);
            allowing.put(who.uuid(), now);
            return now;
        }
    }

    /** Collects the events the use case publishes. */
    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}
