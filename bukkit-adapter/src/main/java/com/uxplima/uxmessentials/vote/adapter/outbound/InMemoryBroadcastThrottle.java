package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.BroadcastThrottle;
import org.jspecify.annotations.NullMarked;

/**
 * In-memory {@link BroadcastThrottle} backing the
 * {@link com.uxplima.uxmessentials.vote.domain.BroadcastType#COOLDOWN_PER_PLAYER} policy: a
 * {@code ConcurrentHashMap<UUID, Instant>} of when each voter was last announced. The state is the
 * plugin's own transient cooldown clock, not operator data, so it does not survive a restart, a
 * player whose stamp is forgotten simply gets announced again, which is harmless.
 *
 * <h2>Concurrency</h2>
 * Reads on the vote-handling path and the write when a broadcast fires are both keyed by the voter's
 * UUID, so the concurrent map serialises the per-voter access and the two never race.
 */
@NullMarked
public final class InMemoryBroadcastThrottle implements BroadcastThrottle {

    private final ConcurrentHashMap<UUID, Instant> lastBroadcast = new ConcurrentHashMap<>();

    @Override
    public Optional<Instant> lastBroadcastAt(PlayerRef voter) {
        Objects.requireNonNull(voter, "voter");
        return Optional.ofNullable(lastBroadcast.get(voter.uuid()));
    }

    @Override
    public void recordBroadcast(PlayerRef voter, Instant at) {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(at, "at");
        lastBroadcast.put(voter.uuid(), at);
    }
}
