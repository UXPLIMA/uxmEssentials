package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.poses.application.StartSit.SitOutcome;
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
 * Pins {@link StartSit}: the three refusals ({@code features.sit} off, the region gate, the one-session
 * invariant) leave no seat and no session, and a success spawns + mounts the seat, records the session with the
 * captured return location, and publishes {@link PoseStarted}. All ports are fakes, no Bukkit.
 */
class StartSitTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Position SEAT = new Position(WORLD, 10.5, 64.5, 20.5, 90f, 0f);
    private static final Position STANDING = new Position(WORLD, 11.0, 64.0, 21.0, 0f, 0f);

    private final PoseSessions sessions = new PoseSessions();
    private final RecordingSeatPort seats = new RecordingSeatPort();
    private final RecordingEvents events = new RecordingEvents();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void deniesWhenTheSitFeatureIsDisabled() {
        StartSit startSit = newStartSit(gateAllowing(true), sitEnabled(false), returnToStart(true));

        assertThat(startSit.start(WHO, SEAT, 90f, false)).isEqualTo(SitOutcome.DISABLED);
        assertThat(seats.spawned).isEmpty();
        assertThat(sessions.isPosing(WHO)).isFalse();
        assertThat(events.published).isEmpty();
    }

    @Test
    void deniesWhenTheRegionGateRefuses() {
        StartSit startSit = newStartSit(gateAllowing(false), sitEnabled(true), returnToStart(true));

        assertThat(startSit.start(WHO, SEAT, 90f, false)).isEqualTo(SitOutcome.DENIED_REGION);
        assertThat(seats.spawned).isEmpty();
        assertThat(sessions.isPosing(WHO)).isFalse();
        assertThat(events.published).isEmpty();
    }

    @Test
    void deniesWhenThePlayerIsAlreadyPosing() {
        sessions.start(new PoseSession(WHO, PoseType.SIT, SEAT, "existing", null, clock.instant()));
        StartSit startSit = newStartSit(gateAllowing(true), sitEnabled(true), returnToStart(true));

        assertThat(startSit.start(WHO, SEAT, 90f, false)).isEqualTo(SitOutcome.ALREADY_POSING);
        assertThat(seats.spawned).isEmpty();
        assertThat(seats.mounted).isEmpty();
    }

    @Test
    void spawnsMountsAndRecordsTheSessionWithTheLiveReturnLocation() {
        StartSit startSit = newStartSit(gateAllowing(true), sitEnabled(true), returnToStart(true));

        assertThat(startSit.start(WHO, SEAT, 90f, false)).isEqualTo(SitOutcome.STARTED);

        assertThat(seats.spawned).containsExactly(SEAT);
        assertThat(seats.mounted).containsExactly(WHO);
        PoseSession session = sessions.current(WHO).orElseThrow();
        assertThat(session.type()).isEqualTo(PoseType.SIT);
        assertThat(session.seatHandle()).isEqualTo(seats.lastHandle.id());
        // return-to-start captured the player's live standing position, not the seat block.
        assertThat(session.returnLocation()).isEqualTo(STANDING);
        assertThat(events.published).singleElement().isInstanceOf(PoseStarted.class);
    }

    @Test
    void recordsTheSeatAsTheReturnLocationWhenReturnToStartIsOff() {
        StartSit startSit = newStartSit(gateAllowing(true), sitEnabled(true), returnToStart(false));

        startSit.start(WHO, SEAT, 90f, false);

        assertThat(sessions.current(WHO).orElseThrow().returnLocation()).isEqualTo(SEAT);
    }

    @Test
    void recordsTheSeatAsTheReturnLocationForAnInPlaceSit() {
        StartSit startSit = newStartSit(gateAllowing(true), sitEnabled(true), returnToStart(true));

        startSit.start(WHO, SEAT, 90f, true);

        // Sitting in place, the seat already is where the player stood, so it is the return location.
        assertThat(sessions.current(WHO).orElseThrow().returnLocation()).isEqualTo(SEAT);
    }

    private StartSit newStartSit(PoseRegionGate gate, boolean sitEnabled, boolean returnToStart) {
        return new StartSit(
                sessions,
                seats,
                gate,
                who -> Optional.of(STANDING),
                events,
                clock,
                sitEnabled,
                returnToStart,
                PoseCooldown.unlimited());
    }

    private static PoseRegionGate gateAllowing(boolean allow) {
        return (who, where, type) -> allow;
    }

    private static boolean sitEnabled(boolean value) {
        return value;
    }

    private static boolean returnToStart(boolean value) {
        return value;
    }

    /** Records what the port was asked to spawn and mount, minting a fresh handle per seat. */
    private static final class RecordingSeatPort implements SeatPort {
        private final List<Position> spawned = new ArrayList<>();
        private final List<PlayerRef> mounted = new ArrayList<>();
        private SeatHandle lastHandle = SeatHandle.of("unset");

        @Override
        public SeatHandle spawnSeat(Position seat, float yaw) {
            spawned.add(seat);
            lastHandle = SeatHandle.of("seat-" + spawned.size());
            return lastHandle;
        }

        @Override
        public void mount(PlayerRef rider, SeatHandle seat) {
            mounted.add(rider);
        }

        @Override
        public void mountOnPlayer(PlayerRef rider, PlayerRef target) {}

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

    /** Collects the events the use case publishes. */
    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}
