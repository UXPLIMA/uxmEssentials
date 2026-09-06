package com.uxplima.uxmessentials.vote.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers the auto-claim toggle on {@link VoteJoinListener}: with auto-claim on, the join drains the offline
 * reward queue (proven by the {@link ApplyQueuedRewards#applyFor} probe hitting the repository); with it off,
 * the join never drains: the queue is left for the player to pay out with {@code /vote claim}. The
 * cache-warm read fires in both cases. {@code applyFor} short-circuits on {@code hasPending}, so a repository
 * that records its {@code hasPending} probe is the observable signal for whether the drain ran.
 */
class VoteJoinListenerAutoClaimTest {

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void autoClaimOnDrainsTheQueueOnJoin() {
        RecordingRepository repository = new RecordingRepository();
        VoteJoinListener listener = listener(repository, true);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        // applyFor probes hasPending; with auto-claim on it runs, so the probe is recorded.
        assertThat(repository.hasPendingCalls).isEqualTo(1);
        // The cache-warm read still fired.
        assertThat(repository.totalsOfCalls).isEqualTo(1);
    }

    @Test
    void autoClaimOffSkipsTheDrainButStillWarmsTheCache() {
        RecordingRepository repository = new RecordingRepository();
        VoteJoinListener listener = listener(repository, false);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        // The drain never ran, so applyFor never probed hasPending.
        assertThat(repository.hasPendingCalls).isEqualTo(0);
        // The cache-warm read is unconditional.
        assertThat(repository.totalsOfCalls).isEqualTo(1);
    }

    private VoteJoinListener listener(VoteRepository repository, boolean autoClaim) {
        ApplyQueuedRewards applyQueued = new ApplyQueuedRewards(repository, (commands, name) -> {});
        return new VoteJoinListener(
                applyQueued, repository, new SyncScheduler(), autoClaim, false, Duration.ZERO, null, null, null);
    }

    /** Inline scheduler: runs every task on the calling thread. */
    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Counts the drain probe ({@code hasPending}) and the cache-warm read ({@code totalsOf}). */
    private static final class RecordingRepository implements VoteRepository {
        int hasPendingCalls = 0;
        int totalsOfCalls = 0;

        @Override
        public boolean hasPending(PlayerRef player) {
            hasPendingCalls++;
            return false;
        }

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            totalsOfCalls++;
            return VoteTally.empty();
        }

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
        public void enqueue(QueuedReward reward) {}

        @Override
        public List<QueuedReward> drainFor(PlayerRef player) {
            return List.of();
        }

        @Override
        public int queuedCount(PlayerRef player) {
            return 0;
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
            return 0L;
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
        public boolean claimPartyFire(int threshold) {
            return false;
        }

        @Override
        public void resetTotals(PlayerRef player) {}

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}
    }
}
