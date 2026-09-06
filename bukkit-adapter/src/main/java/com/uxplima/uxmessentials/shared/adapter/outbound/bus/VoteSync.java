package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.persistence.vote.CachedVoteRepository;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.network.VoteCounterChanged;
import com.uxplima.uxmessentials.shared.network.VotePartyFired;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.application.port.ForwardingVoteRepository;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import org.jspecify.annotations.NullMarked;

/**
 * The vote context's cross-server sync seam, the same shape as {@link HomeSync} and {@link WarpSync}, applied
 * to the server-wide vote-party counter. The party counter is one global figure backed by the shared database,
 * so (like warps) the whole cached value is the invalidation unit:
 *
 * <ul>
 *   <li><b>Outbound (counter)</b>: {@link #repository(CachedVoteRepository, BusPublisher)} wraps the cached
 *       repository so every counter mutation ({@code incrementAndGetPartyCount}, {@code setPartyCount},
 *       {@code claimPartyFire}) publishes a {@link VoteCounterChanged} frame after the durable write commits
 *       peers learn the running total advanced and drop their cached copy.
 *   <li><b>Outbound (party fire)</b>: {@link #partyPublisher(BusPublisher)} returns a domain-event consumer
 *       that, on a local {@link VotePartyTriggered}, publishes a {@link VotePartyFired} frame so peers run
 *       their own party celebration once, in lockstep, rather than each racing to detect the crossing.
 *   <li><b>Inbound</b>: {@link #listener(CachedVoteRepository, VoteBroadcaster, Set)} returns a
 *       {@link RemoteSyncListener} that, on a remote {@link VoteCounterChanged}, drops the cached counter, and
 *       on a remote {@link VotePartyFired}, drops the cached counter <em>and</em> re-broadcasts the party
 *       announcement on the configured channels. It never applies any reward, the origin backend already paid
 *       its players out, and the shared DB holds the authoritative reset; a peer only echoes the announcement.
 * </ul>
 *
 * <p>The decorator wraps the <em>same</em> cache the vote commands read, so the loop closes: a write here emits
 * a frame, the peer's listener drops its cached counter there. The bus client drops a frame that originated on
 * this backend, so a backend never re-broadcasts its own party.
 */
@NullMarked
public final class VoteSync {

    private VoteSync() {}

    /** A {@link VoteRepository} that broadcasts a {@link VoteCounterChanged} after every counter mutation. */
    public static VoteRepository repository(CachedVoteRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /**
     * A listener that drops the cached counter on a remote {@link VoteCounterChanged}, and on a remote
     * {@link VotePartyFired} drops the counter and re-broadcasts {@link VoteMessageKey#VOTEPARTY_REACHED} on
     * {@code channels}. It applies no reward; the origin already paid its players out.
     */
    public static RemoteSyncListener listener(
            CachedVoteRepository cache, VoteBroadcaster broadcaster, Set<BroadcastChannel> channels) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(broadcaster, "broadcaster");
        Objects.requireNonNull(channels, "channels");
        Set<BroadcastChannel> safeChannels = Set.copyOf(channels);
        return message -> {
            if (message instanceof VoteCounterChanged) {
                cache.invalidate();
            } else if (message instanceof VotePartyFired fired) {
                cache.invalidate();
                broadcaster.broadcast(
                        VoteMessageKey.VOTEPARTY_REACHED,
                        Map.of("threshold", Integer.toString(fired.threshold())),
                        safeChannels);
            }
        };
    }

    /**
     * A domain-event consumer that publishes a {@link VotePartyFired} when a {@link VotePartyTriggered} fires
     * locally, so peers celebrate the same party. Any other event is ignored.
     */
    public static Consumer<DomainEvent> partyPublisher(BusPublisher bus) {
        Objects.requireNonNull(bus, "bus");
        return event -> {
            if (event instanceof VotePartyTriggered triggered) {
                bus.publish(new VotePartyFired(bus.serverId(), triggered.threshold()));
            }
        };
    }

    /** Forwards every call to the cached delegate, then announces each counter mutation on the bus. */
    private static final class Broadcasting extends ForwardingVoteRepository {

        private final BusPublisher bus;

        Broadcasting(VoteRepository delegate, BusPublisher bus) {
            super(delegate);
            this.bus = bus;
        }

        @Override
        public void setPartyCount(int count) {
            delegate.setPartyCount(count);
            announce();
        }

        @Override
        public int incrementAndGetPartyCount() {
            int updated = delegate.incrementAndGetPartyCount();
            announce();
            return updated;
        }

        @Override
        public boolean claimPartyFire(int threshold) {
            boolean won = delegate.claimPartyFire(threshold);
            // The reset moves the durable counter regardless of who won the claim, and invalidation is
            // idempotent, so announce unconditionally rather than gating on the win.
            announce();
            return won;
        }

        private void announce() {
            bus.publish(new VoteCounterChanged(bus.serverId()));
        }
    }
}
