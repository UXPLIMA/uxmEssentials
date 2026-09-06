package com.uxplima.uxmessentials.presence.application.port;

import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port over the transient per-player presence map. The adapter backs it with a
 * {@code ConcurrentHashMap<UUID, PlayerPresence>} and applies every mutation through {@code compute} /
 * {@code computeIfAbsent}, so a reader on any thread, a sync activity listener, the async AFK sweep, a
 * cross-context vanish lookup, always sees a coherent immutable aggregate and concurrent transitions never
 * lose an update (docs/02-concurrency.md §6.9: the sync-listener / async-reader split).
 *
 * <p>The state is in-memory only: presence persists nothing in v1 (no migration, no DB). A player's presence
 * is seeded on join, re-stamped by the activity listeners, flipped by the AFK/vanish use cases and the sweep,
 * and dropped on quit. The {@link #snapshotAll} read is what the AFK sweep iterates without holding the map.
 */
public interface PresenceStore {

    /** The current presence for {@code who}, creating the neutral active state if none exists yet. */
    PlayerPresence current(PlayerRef who);

    /**
     * Atomically replace {@code who}'s presence by applying {@code mutator} to the current one (or a freshly
     * active state when absent) and return the new value. The mutator must be a pure function of its input: it
     * may be retried under contention, and must not perform I/O or touch the live player.
     */
    PlayerPresence update(PlayerRef who, UnaryOperator<PlayerPresence> mutator);

    /** Drop {@code who}'s presence entirely; called on quit so a disconnected player holds no state. */
    void forget(PlayerRef who);

    /**
     * A point-in-time copy of every tracked player's presence, for the AFK sweep to scan without iterating the
     * live map. The returned entries are immutable aggregates keyed by the player they describe.
     */
    java.util.Map<PlayerRef, PlayerPresence> snapshotAll();
}
