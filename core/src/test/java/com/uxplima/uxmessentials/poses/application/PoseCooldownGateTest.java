package com.uxplima.uxmessentials.poses.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.poses.application.StartSit.SitOutcome;
import com.uxplima.uxmessentials.poses.application.port.SeatHandle;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the shared pose cooldown pre-check as it is enforced through a start use case ({@link StartSit} stands in
 * for all six poses: they all consult the same {@link PoseCooldown}). A player still inside the window is turned
 * away with {@link SitOutcome#ON_COOLDOWN} and no seat is spawned and no session recorded; once the window elapses
 * the pose starts and the clock is stamped again; a player who holds no {@code poses.cooldown.<seconds>} tier waits
 * for nothing; and the {@code poses.cooldown.bypass} node skips the gate entirely. The cooldown port is a fake so
 * the test never touches PDC or a real clock.
 */
class PoseCooldownGateTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Position SEAT = new Position(WORLD, 10.5, 64.5, 20.5, 90f, 0f);

    private final PoseSessions sessions = new PoseSessions();
    private final RecordingSeatPort seats = new RecordingSeatPort();
    private final RecordingEvents events = new RecordingEvents();
    private final FakeCooldowns cooldowns = new FakeCooldowns();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void aPlayerWithinTheCooldownWindowIsDeniedAndNoPoseStarts() {
        cooldowns.stampSeconds = 10;
        StartSit startSit = newStartSit();
        startSit.start(WHO, SEAT, 90f, false); // first sit stamps the poses cooldown
        stopAndClear();

        SitOutcome second = startSit.start(WHO, SEAT, 90f, false);

        assertThat(second).isEqualTo(SitOutcome.ON_COOLDOWN);
        // No seat spawned and no session recorded for the refused second attempt: the pose never started.
        assertThat(seats.spawned).hasSize(1); // only the first, allowed sit spawned a seat
        assertThat(sessions.isPosing(WHO)).isFalse();
    }

    @Test
    void afterTheWindowElapsesThePoseStartsAgain() {
        cooldowns.stampSeconds = 10;
        StartSit startSit = newStartSit();
        startSit.start(WHO, SEAT, 90f, false);
        stopAndClear();

        cooldowns.now = cooldowns.now + 11_000; // fast-forward past the ten-second wait
        assertThat(startSit.start(WHO, SEAT, 90f, false)).isEqualTo(SitOutcome.STARTED);
        assertThat(sessions.isPosing(WHO)).isTrue();
    }

    @Test
    void aPlayerWithNoCooldownTierWaitsForNothing() {
        cooldowns.stampSeconds = 0; // no poses.cooldown.<seconds> node resolves, the zero default
        StartSit startSit = newStartSit();
        startSit.start(WHO, SEAT, 90f, false);
        stopAndClear();

        // Immediately posing again is allowed: a zero-length cooldown stamps nothing.
        assertThat(startSit.start(WHO, SEAT, 90f, false)).isEqualTo(SitOutcome.STARTED);
    }

    @Test
    void theBypassNodeSkipsTheGate() {
        cooldowns.stampSeconds = 10;
        cooldowns.bypass.add(WHO.uuid());
        StartSit startSit = newStartSit();
        startSit.start(WHO, SEAT, 90f, false);
        stopAndClear();

        // Bypassed: the second sit begins at once even though ten seconds have not elapsed.
        assertThat(startSit.start(WHO, SEAT, 90f, false)).isEqualTo(SitOutcome.STARTED);
    }

    /** End the current pose and drop it from the registry so the next start sees a clean, non-posing player. */
    private void stopAndClear() {
        sessions.stop(WHO);
    }

    private StartSit newStartSit() {
        return new StartSit(
                sessions,
                seats,
                (who, where, type) -> true,
                who -> Optional.of(SEAT),
                events,
                clock,
                true,
                true,
                PoseCooldown.backedBy(cooldowns));
    }

    /**
     * A minimal {@link Cooldowns} that models a single "ready-at" per player against a virtual clock: a stamp sets
     * the ready time {@code stampSeconds} ahead, a check fails until the clock passes it, and a bypassed player is
     * always ready. Only the tiered {@code check}/{@code stamp} pair the poses gate uses is meaningful here.
     */
    private static final class FakeCooldowns implements Cooldowns {
        private final Map<UUID, Long> readyAt = new HashMap<>();
        private final Set<UUID> bypass = new HashSet<>();
        private long now = 1_000_000L;
        private long stampSeconds = 0;

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            if (bypass.contains(who.uuid())) {
                return Result.ok();
            }
            long ready = readyAt.getOrDefault(who.uuid(), 0L);
            return now >= ready ? Result.ok() : Result.err(Duration.ofMillis(ready - now));
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            if (bypass.contains(who.uuid()) || stampSeconds <= 0) {
                return;
            }
            readyAt.put(who.uuid(), now + stampSeconds * 1000L);
        }

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class RecordingSeatPort implements SeatPort {
        private final List<Position> spawned = new ArrayList<>();
        private SeatHandle lastHandle = SeatHandle.of("unset");

        @Override
        public SeatHandle spawnSeat(Position seat, float yaw) {
            spawned.add(seat);
            lastHandle = SeatHandle.of("seat-" + spawned.size());
            return lastHandle;
        }

        @Override
        public void mount(PlayerRef rider, SeatHandle seat) {}

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

    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }
}
