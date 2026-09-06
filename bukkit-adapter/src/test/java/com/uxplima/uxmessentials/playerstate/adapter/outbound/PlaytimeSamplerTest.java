package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.playerstate.application.port.AfkStatus;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Drives the {@link PlaytimeSampler} with a fake roster, a fake {@link AfkStatus}, a fake
 * {@link PlaytimeRepository}, and a Mockito {@link Scheduler} whose {@code onGlobal}/{@code async} run inline (the
 * same shape {@code SalaryTaskTest} uses), capturing the {@code asyncAfter} callback to fire one tick by hand. It
 * proves the headline rule of the AFK split: a non-AFK player's interval lands as active seconds, an AFK player's
 * as AFK seconds, and the loop re-arms itself for the next tick. Disabled sampling never schedules.
 */
class PlaytimeSamplerTest {

    private static final Duration INTERVAL = Duration.ofMinutes(1);
    private static final Clock FIXED =
            Clock.fixed(LocalDate.of(2026, 6, 22).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private Scheduler scheduler;
    private FakeAfkStatus afk;
    private FakeRepository repo;
    private PlayerRef active;
    private PlayerRef idle;

    @BeforeEach
    void setUp() {
        scheduler = mock(Scheduler.class);
        runInline(scheduler);
        afk = new FakeAfkStatus();
        repo = new FakeRepository();
        active = new PlayerRef(UUID.randomUUID(), "Active");
        idle = new PlayerRef(UUID.randomUUID(), "Idle");
    }

    @Test
    void disabledSamplingNeverSchedules() {
        sampler(false, List.of(active)).start();

        verify(scheduler, never()).asyncAfter(any(), any());
    }

    @Test
    void aTickCreditsActiveSecondsForANonAfkPlayerAndAfkSecondsForAnAfkPlayer() {
        afk.mark(idle); // idle is AFK; active is not

        fireOneTick(sampler(true, List.of(active, idle)));

        PlaytimeSummary activeSummary = repo.summaryOf(active.uuid(), LocalDate.now(FIXED));
        assertThat(activeSummary.todayActive().toSeconds()).isEqualTo(60L);
        assertThat(activeSummary.todayAfk()).isEqualTo(Duration.ZERO);

        PlaytimeSummary idleSummary = repo.summaryOf(idle.uuid(), LocalDate.now(FIXED));
        assertThat(idleSummary.todayActive()).isEqualTo(Duration.ZERO);
        assertThat(idleSummary.todayAfk().toSeconds()).isEqualTo(60L);
    }

    @Test
    void theLoopReArmsItselfAfterEachTick() {
        PlaytimeSampler sampler = sampler(true, List.of(active));

        sampler.start();
        // start() schedules once; firing that tick must schedule the next: the self-rescheduling loop.
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).asyncAfter(any(), captor.capture());
        captor.getValue().run();

        verify(scheduler, org.mockito.Mockito.times(2)).asyncAfter(any(), any());
    }

    private PlaytimeSampler sampler(boolean enabled, List<PlayerRef> roster) {
        return new PlaytimeSampler(scheduler, repo, afk, () -> roster, FIXED, enabled, INTERVAL);
    }

    private void fireOneTick(PlaytimeSampler sampler) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        sampler.start();
        verify(scheduler).asyncAfter(any(), captor.capture());
        captor.getValue().run();
    }

    private static void runInline(Scheduler scheduler) {
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .onGlobal(any(Runnable.class));
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .async(any(Runnable.class));
    }

    /** A mutable AFK seam: a player marked here reports AFK, everyone else active. */
    private static final class FakeAfkStatus implements AfkStatus {
        private final java.util.Set<UUID> afkSet = ConcurrentHashMap.newKeySet();

        void mark(PlayerRef who) {
            afkSet.add(who.uuid());
        }

        @Override
        public boolean isAfk(PlayerRef who) {
            return afkSet.contains(who.uuid());
        }
    }

    /** A map-backed ledger accumulating per-(player, day) deltas, summed back per window for assertions. */
    private static final class FakeRepository implements PlaytimeRepository {
        private final Map<UUID, long[]> totals = new ConcurrentHashMap<>(); // [active, afk]

        @Override
        public void addSeconds(UUID uuid, LocalDate day, long activeDelta, long afkDelta) {
            totals.merge(uuid, new long[] {activeDelta, afkDelta}, (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]});
        }

        @Override
        public PlaytimeSummary summaryOf(UUID uuid, LocalDate today) {
            long[] t = totals.getOrDefault(uuid, new long[] {0L, 0L});
            return PlaytimeSummary.ofSeconds(t[0], t[1], t[0], t[1], t[0], t[1], t[0], t[1]);
        }

        @Override
        public void reset(UUID uuid) {
            totals.remove(uuid);
        }

        @Override
        public void resetAll() {
            totals.clear();
        }
    }
}
