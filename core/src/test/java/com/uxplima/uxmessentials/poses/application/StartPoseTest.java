package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.poses.application.StartPose.PoseOutcome;
import com.uxplima.uxmessentials.poses.application.port.PosePort;
import com.uxplima.uxmessentials.poses.application.port.PoseRegionGate;
import com.uxplima.uxmessentials.poses.application.port.SeatHandle;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.poses.application.port.Snores;
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
 * Pins {@link StartPose}: each free pose ({@code /lay}, {@code /bellyflop}, {@code /spin}) is denied when its
 * feature switch is off, when the region gate refuses, or when the player already poses; and a success anchors the
 * player on a seat, renders the right pose, records a session of that type with the on-the-spot return location,
 * and publishes {@link PoseStarted}. Only {@code /lay} snores, and only when snoring is enabled. All ports are
 * fakes, no Bukkit.
 */
class StartPoseTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Position AT = new Position(WORLD, 10.5, 64.0, 20.5, 90f, 0f);

    private final PoseSessions sessions = new PoseSessions();
    private final RecordingSeatPort seats = new RecordingSeatPort();
    private final RecordingPosePort posePort = new RecordingPosePort();
    private final RecordingSnores snores = new RecordingSnores();
    private final RecordingEvents events = new RecordingEvents();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void deniesEachPoseWhenItsFeatureIsDisabled() {
        StartPose allOff = newStartPose(gateAllowing(true), false, false, false, true);

        assertThat(allOff.start(WHO, PoseType.LAY, AT, 90f)).isEqualTo(PoseOutcome.DISABLED);
        assertThat(allOff.start(WHO, PoseType.BELLYFLOP, AT, 90f)).isEqualTo(PoseOutcome.DISABLED);
        assertThat(allOff.start(WHO, PoseType.SPIN, AT, 90f)).isEqualTo(PoseOutcome.DISABLED);
        assertThat(seats.spawned).isEmpty();
        assertThat(posePort.applied).isEmpty();
        assertThat(sessions.isPosing(WHO)).isFalse();
        assertThat(events.published).isEmpty();
    }

    @Test
    void deniesWhenTheRegionGateRefuses() {
        StartPose startPose = newStartPose(gateAllowing(false), true, true, true, true);

        assertThat(startPose.start(WHO, PoseType.LAY, AT, 90f)).isEqualTo(PoseOutcome.DENIED_REGION);
        assertThat(seats.spawned).isEmpty();
        assertThat(posePort.applied).isEmpty();
        assertThat(sessions.isPosing(WHO)).isFalse();
    }

    @Test
    void deniesWhenThePlayerIsAlreadyPosing() {
        sessions.start(new PoseSession(WHO, PoseType.SIT, AT, "existing", null, clock.instant()));
        StartPose startPose = newStartPose(gateAllowing(true), true, true, true, true);

        assertThat(startPose.start(WHO, PoseType.SPIN, AT, 90f)).isEqualTo(PoseOutcome.ALREADY_POSING);
        assertThat(seats.spawned).isEmpty();
        assertThat(posePort.applied).isEmpty();
    }

    @Test
    void layingAnchorsAppliesTheLayPoseSnoresAndRecordsTheSession() {
        StartPose startPose = newStartPose(gateAllowing(true), true, true, true, true);

        assertThat(startPose.start(WHO, PoseType.LAY, AT, 90f)).isEqualTo(PoseOutcome.STARTED);

        assertThat(seats.spawned).containsExactly(AT);
        assertThat(seats.mounted).containsExactly(WHO);
        assertThat(posePort.applied).containsExactly(PoseType.LAY);
        assertThat(snores.snoring).containsExactly(WHO);
        PoseSession session = sessions.current(WHO).orElseThrow();
        assertThat(session.type()).isEqualTo(PoseType.LAY);
        assertThat(session.seatHandle()).isEqualTo(seats.lastHandle.id());
        // A free pose is struck on the spot, so the seat position is also the return location.
        assertThat(session.returnLocation()).isEqualTo(AT);
        assertThat(events.published).singleElement().isInstanceOf(PoseStarted.class);
    }

    @Test
    void bellyfloppingAppliesTheBellyflopPoseAndDoesNotSnore() {
        StartPose startPose = newStartPose(gateAllowing(true), true, true, true, true);

        assertThat(startPose.start(WHO, PoseType.BELLYFLOP, AT, 90f)).isEqualTo(PoseOutcome.STARTED);

        assertThat(posePort.applied).containsExactly(PoseType.BELLYFLOP);
        assertThat(snores.snoring).isEmpty();
        assertThat(sessions.current(WHO).orElseThrow().type()).isEqualTo(PoseType.BELLYFLOP);
    }

    @Test
    void spinningAppliesTheSpinPoseAndDoesNotSnore() {
        StartPose startPose = newStartPose(gateAllowing(true), true, true, true, true);

        assertThat(startPose.start(WHO, PoseType.SPIN, AT, 90f)).isEqualTo(PoseOutcome.STARTED);

        assertThat(posePort.applied).containsExactly(PoseType.SPIN);
        assertThat(snores.snoring).isEmpty();
        assertThat(sessions.current(WHO).orElseThrow().type()).isEqualTo(PoseType.SPIN);
    }

    @Test
    void layingDoesNotSnoreWhenSnoringIsDisabled() {
        StartPose startPose = newStartPose(gateAllowing(true), true, true, true, false);

        startPose.start(WHO, PoseType.LAY, AT, 90f);

        assertThat(posePort.applied).containsExactly(PoseType.LAY);
        assertThat(snores.snoring).isEmpty();
    }

    private StartPose newStartPose(PoseRegionGate gate, boolean lay, boolean bellyflop, boolean spin, boolean snore) {
        return new StartPose(
                sessions,
                seats,
                gate,
                posePort,
                snores,
                events,
                clock,
                lay,
                bellyflop,
                spin,
                snore,
                PoseCooldown.unlimited());
    }

    private static PoseRegionGate gateAllowing(boolean allow) {
        return (who, where, type) -> allow;
    }

    /** Records what the seat port was asked to spawn and mount, minting a fresh handle per seat. */
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

    /** Records which poses were rendered and cleared. */
    private static final class RecordingPosePort implements PosePort {
        private final List<PoseType> applied = new ArrayList<>();
        private final List<PlayerRef> cleared = new ArrayList<>();

        @Override
        public void applyPose(PlayerRef who, PoseType pose) {
            applied.add(pose);
        }

        @Override
        public void clearPose(PlayerRef who) {
            cleared.add(who);
        }
    }

    /** Records who was asked to start and stop snoring. */
    private static final class RecordingSnores implements Snores {
        private final List<PlayerRef> snoring = new ArrayList<>();

        @Override
        public void startSnoring(PlayerRef who) {
            snoring.add(who);
        }

        @Override
        public void stopSnoring(PlayerRef who) {
            snoring.remove(who);
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
