package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.persistence.vote.CachedVoteRepository;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.VoteCounterChanged;
import com.uxplima.uxmessentials.shared.network.VotePartyFired;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of the vote context's cross-server sync seam. The three seams are exercised in isolation
 * the inbound listener (invalidate + echo-broadcast, never reward), the outbound counter decorator (announce
 * each counter mutation, pass return values through, stay silent on non-counter reads), and the outbound
 * party publisher (a local party fire becomes a {@link VotePartyFired}): all against fakes, no Bukkit.
 */
final class VoteSyncTest {

    private static final String SERVER_ID = "lobby-1";

    @Test
    void counterChangedInvalidatesTheCacheAndAppliesNoReward() {
        ReadCountingRepository delegate = new ReadCountingRepository(7);
        CachedVoteRepository cache = new CachedVoteRepository(delegate);
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        cache.partyCount(); // prime the cached counter from the delegate (one read)
        assertThat(delegate.reads()).isEqualTo(1);

        RemoteSyncListener listener = VoteSync.listener(cache, broadcaster, Set.of(BroadcastChannel.CHAT));
        listener.onRemoteChange(new VoteCounterChanged("backend-2"));

        cache.partyCount(); // a fresh read reaches the delegate again only if the cache was dropped
        assertThat(delegate.reads()).isEqualTo(2);
        assertThat(broadcaster.broadcasts).isEmpty();
    }

    @Test
    void partyFiredInvalidatesAndReBroadcastsTheAnnouncementWithoutRewarding() {
        ReadCountingRepository delegate = new ReadCountingRepository(40);
        CachedVoteRepository cache = new CachedVoteRepository(delegate);
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        cache.partyCount();
        assertThat(delegate.reads()).isEqualTo(1);
        Set<BroadcastChannel> channels = Set.of(BroadcastChannel.CHAT, BroadcastChannel.ACTION_BAR);

        RemoteSyncListener listener = VoteSync.listener(cache, broadcaster, channels);
        listener.onRemoteChange(new VotePartyFired("backend-2", 25));

        cache.partyCount();
        assertThat(delegate.reads()).isEqualTo(2);
        assertThat(broadcaster.broadcasts).hasSize(1);
        RecordingBroadcaster.Sent sent = broadcaster.broadcasts.get(0);
        assertThat(sent.key()).isEqualTo(VoteMessageKey.VOTEPARTY_REACHED);
        assertThat(sent.placeholders()).containsExactlyEntriesOf(Map.of("threshold", "25"));
        assertThat(sent.channels()).isEqualTo(channels);
        // The listener must never pay out: a peer only echoes the announcement; the origin already rewarded.
        assertThat(delegate.rewardApplied).isFalse();
    }

    @Test
    void anUnrelatedFrameIsIgnored() {
        ReadCountingRepository delegate = new ReadCountingRepository(3);
        CachedVoteRepository cache = new CachedVoteRepository(delegate);
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        cache.partyCount();
        assertThat(delegate.reads()).isEqualTo(1);

        RemoteSyncListener listener = VoteSync.listener(cache, broadcaster, Set.of(BroadcastChannel.CHAT));
        listener.onRemoteChange(new HomeChanged("backend-2", UUID.randomUUID()));

        cache.partyCount();
        assertThat(delegate.reads()).isEqualTo(1); // cache untouched
        assertThat(broadcaster.broadcasts).isEmpty();
    }

    @Test
    void incrementAnnouncesACounterChangeAndPassesTheValueThrough() {
        ReadCountingRepository delegate = new ReadCountingRepository(0);
        delegate.nextIncrementResult = 12;
        CachedVoteRepository cache = new CachedVoteRepository(delegate);
        RecordingPublisher publisher = new RecordingPublisher();
        VoteRepository repository = VoteSync.repository(cache, publisher);

        int returned = repository.incrementAndGetPartyCount();

        assertThat(returned).isEqualTo(12);
        assertThat(publisher.published).containsExactly(new VoteCounterChanged(SERVER_ID));
    }

    @Test
    void setPartyCountAnnouncesACounterChange() {
        CachedVoteRepository cache = new CachedVoteRepository(new ReadCountingRepository(0));
        RecordingPublisher publisher = new RecordingPublisher();
        VoteRepository repository = VoteSync.repository(cache, publisher);

        repository.setPartyCount(5);

        assertThat(publisher.published).containsExactly(new VoteCounterChanged(SERVER_ID));
    }

    @Test
    void claimPartyFireAnnouncesACounterChangeAndPassesTheResultThrough() {
        ReadCountingRepository delegate = new ReadCountingRepository(30);
        delegate.nextClaimResult = true;
        CachedVoteRepository cache = new CachedVoteRepository(delegate);
        RecordingPublisher publisher = new RecordingPublisher();
        VoteRepository repository = VoteSync.repository(cache, publisher);

        boolean won = repository.claimPartyFire(25);

        assertThat(won).isTrue();
        assertThat(publisher.published).containsExactly(new VoteCounterChanged(SERVER_ID));
    }

    @Test
    void aNonCounterMethodPublishesNothing() {
        ReadCountingRepository delegate = new ReadCountingRepository(0);
        CachedVoteRepository cache = new CachedVoteRepository(delegate);
        RecordingPublisher publisher = new RecordingPublisher();
        VoteRepository repository = VoteSync.repository(cache, publisher);

        VoteTally tally = repository.totalsOf(new PlayerRef(UUID.randomUUID(), "Sira"));

        assertThat(tally).isEqualTo(VoteTally.empty());
        assertThat(publisher.published).isEmpty();
    }

    @Test
    void partyPublisherEmitsAFiredFrameForALocalPartyTrigger() {
        RecordingPublisher publisher = new RecordingPublisher();
        Consumer<DomainEvent> partyPublisher = VoteSync.partyPublisher(publisher);

        partyPublisher.accept(new VotePartyTriggered(25));

        assertThat(publisher.published).containsExactly(new VotePartyFired(SERVER_ID, 25));
    }

    @Test
    void partyPublisherIgnoresAnUnrelatedEvent() {
        RecordingPublisher publisher = new RecordingPublisher();
        Consumer<DomainEvent> partyPublisher = VoteSync.partyPublisher(publisher);

        partyPublisher.accept(new UnrelatedEvent());

        assertThat(publisher.published).isEmpty();
    }

    /** A {@link BusPublisher} that records every published frame and stamps a fixed origin. */
    private static final class RecordingPublisher implements BusPublisher {
        final List<NetworkMessage> published = new ArrayList<>();

        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return SERVER_ID;
        }
    }

    /** A {@link VoteBroadcaster} that captures every broadcast call rather than touching Bukkit. */
    private static final class RecordingBroadcaster implements VoteBroadcaster {
        final List<Sent> broadcasts = new ArrayList<>();

        @Override
        public void broadcast(MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> channels) {
            broadcasts.add(new Sent(key, Map.copyOf(placeholders), Set.copyOf(channels)));
        }

        record Sent(MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> channels) {}
    }

    /**
     * A {@link VoteRepository} whose {@link #partyCount()} reads are counted, so a test can detect whether the
     * surrounding cache was invalidated (an invalidated cache re-reads the delegate). The drain path flips
     * {@link #rewardApplied} so a test can assert the sync listener never triggers a payout.
     */
    private static final class ReadCountingRepository implements VoteRepository {
        private final int storedCount;
        private final AtomicInteger reads = new AtomicInteger();
        int nextIncrementResult = 1;
        boolean nextClaimResult;
        boolean rewardApplied;

        ReadCountingRepository(int storedCount) {
            this.storedCount = storedCount;
        }

        int reads() {
            return reads.get();
        }

        @Override
        public int partyCount() {
            reads.incrementAndGet();
            return storedCount;
        }

        @Override
        public void setPartyCount(int count) {}

        @Override
        public int incrementAndGetPartyCount() {
            return nextIncrementResult;
        }

        @Override
        public boolean claimPartyFire(int threshold) {
            return nextClaimResult;
        }

        @Override
        public void enqueue(QueuedReward reward) {
            rewardApplied = true;
        }

        @Override
        public List<QueuedReward> drainFor(PlayerRef player) {
            rewardApplied = true;
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
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}

        @Override
        public void resetTotals(PlayerRef player) {}
    }

    /** A throwaway domain event the party publisher must ignore. */
    private record UnrelatedEvent() implements DomainEvent {}
}
