package com.uxplima.uxmessentials.vote.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vote.adapter.outbound.VoteDiscordSettings;
import com.uxplima.uxmessentials.vote.adapter.outbound.VoteDiscordSettings.TopVoter;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link VoteWiring#wireDiscord}: the disabled-by-default contract and the enabled path. Disabled
 * (an empty URL) must subscribe nothing on the event bus and schedule no task; enabled with a valid URL must
 * subscribe the notifier and, when the top-voter feature is on, schedule exactly one repeating task.
 */
class VoteDiscordWiringTest {

    private static final String VALID_URL = "https://discord.com/api/webhooks/1/token";

    @Test
    void disabledSettingsSubscribeNothingAndScheduleNothing() {
        CountingScheduler scheduler = new CountingScheduler();
        InProcessDomainEventPublisher events = new InProcessDomainEventPublisher(new SilentLogger());
        KernelPorts kernel = kernel(scheduler);

        VoteWiring.Discord discord = VoteWiring.wireDiscord(disabled(), kernel, new StubRepository(), events);

        assertThat(discord.notifier()).isNull();
        assertThat(discord.topVoterTask()).isNull();
        assertThat(scheduler.repeatCount.get()).isZero();
    }

    @Test
    void malformedUrlSubscribesNothingAndScheduleNothing() {
        CountingScheduler scheduler = new CountingScheduler();
        InProcessDomainEventPublisher events = new InProcessDomainEventPublisher(new SilentLogger());
        KernelPorts kernel = kernel(scheduler);

        // enabled (non-blank) but not a valid http/https URL -> the notifier reports inactive, wired no further.
        VoteWiring.Discord discord =
                VoteWiring.wireDiscord(enabled("not-a-url", false), kernel, new StubRepository(), events);

        assertThat(discord.notifier()).isNull();
        assertThat(discord.topVoterTask()).isNull();
        assertThat(scheduler.repeatCount.get()).isZero();
    }

    @Test
    void enabledSubscribesTheNotifierButSchedulesNoTaskWhenTopVoterOff() {
        CountingScheduler scheduler = new CountingScheduler();
        InProcessDomainEventPublisher events = new InProcessDomainEventPublisher(new SilentLogger());
        KernelPorts kernel = kernel(scheduler);

        VoteWiring.Discord discord =
                VoteWiring.wireDiscord(enabled(VALID_URL, false), kernel, new StubRepository(), events);

        assertThat(discord.notifier()).isNotNull();
        assertThat(java.util.Objects.requireNonNull(discord.notifier()).active())
                .isTrue();
        assertThat(discord.topVoterTask()).isNull();
        assertThat(scheduler.repeatCount.get()).isZero();
    }

    @Test
    void enabledWithTopVoterSubscribesAndSchedulesExactlyOneTask() {
        CountingScheduler scheduler = new CountingScheduler();
        InProcessDomainEventPublisher events = new InProcessDomainEventPublisher(new SilentLogger());
        KernelPorts kernel = kernel(scheduler);

        VoteWiring.Discord discord;
        // The build path must not throw; the top-voter task is scheduled (its body never runs here).
        discord = VoteWiring.wireDiscord(enabled(VALID_URL, true), kernel, new StubRepository(), events);

        assertThat(discord.notifier()).isNotNull();
        assertThat(discord.topVoterTask()).isNotNull();
        assertThat(scheduler.repeatCount.get()).isEqualTo(1);
        AutoCloseable task = java.util.Objects.requireNonNull(discord.topVoterTask());
        assertThatCode(task::close).doesNotThrowAnyException();
    }

    private static KernelPorts kernel(Scheduler scheduler) {
        KernelPorts kernel = mock(KernelPorts.class);
        when(kernel.scheduler()).thenReturn(scheduler);
        when(kernel.log()).thenReturn(new SilentLogger());
        when(kernel.playerLookup()).thenReturn(mock(PlayerLookup.class));
        return kernel;
    }

    private static VoteDiscordSettings disabled() {
        return settings(false, "", false);
    }

    private static VoteDiscordSettings enabled(String url, boolean topVoter) {
        return settings(true, url, topVoter);
    }

    private static VoteDiscordSettings settings(boolean enabled, String url, boolean topVoter) {
        return new VoteDiscordSettings(
                enabled,
                url,
                true,
                "{player} voted on {service}",
                true,
                "party {threshold}",
                new TopVoter(topVoter, VotePeriod.MONTHLY, 10, 1440, "Top Voters"));
    }

    /** Records how many repeating tasks were scheduled; never runs the task body. */
    private static final class CountingScheduler implements Scheduler {
        private final AtomicInteger repeatCount = new AtomicInteger();

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            repeatCount.incrementAndGet();
            return () -> {};
        }
    }

    private static final class SilentLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A do-nothing repository; the top-voter query is never reached because the task body never runs. */
    private static final class StubRepository implements VoteRepository {
        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public void setPartyCount(int count) {}

        @Override
        public int incrementAndGetPartyCount() {
            return 0;
        }

        @Override
        public boolean claimPartyFire(int threshold) {
            return false;
        }

        @Override
        public void enqueue(QueuedReward reward) {}

        @Override
        public List<QueuedReward> drainFor(PlayerRef player) {
            return List.of();
        }

        @Override
        public boolean hasPending(PlayerRef player) {
            return false;
        }

        @Override
        public int queuedCount(PlayerRef player) {
            return 0;
        }

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            return VoteTally.empty();
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {}

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            return List.of();
        }

        @Override
        public void markPartyParticipant(PlayerRef player) {}

        @Override
        public Set<UUID> partyParticipants() {
            return Set.of();
        }

        @Override
        public void clearPartyParticipants() {}

        @Override
        public long partyPeriodKey() {
            return 0;
        }

        @Override
        public void setPartyPeriodKey(long key) {}

        @Override
        public int thresholdOverride() {
            return 0;
        }

        @Override
        public void setThresholdOverride(int override) {}

        @Override
        public void resetTotals(PlayerRef player) {}

        @Override
        public Optional<java.time.Instant> lastVoteAtSite(PlayerRef player, String site) {
            return Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, java.time.Instant at) {}
    }
}
