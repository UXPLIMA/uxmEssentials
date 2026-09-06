package com.uxplima.uxmessentials.playerstate.application.port;

import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port over the transient per-player state map. The adapter backs it with a
 * {@code ConcurrentHashMap<UUID, PlayerStateSnapshot>} and applies every mutation through {@code compute} /
 * {@code computeIfAbsent}, so a reader on another region thread always sees a coherent immutable snapshot and
 * concurrent toggles never lose an update (docs/02-concurrency §the playerstate snapshot pattern).
 *
 * <p>The state is in-memory only: playerstate persists nothing (no migration, no DB). A player's snapshot is
 * seeded on join, mutated by the toggle/set use cases, re-read by the reconciler, and dropped on quit.
 */
public interface PlayerStateStore {

    /** The current snapshot for {@code who}, creating the neutral initial state if none exists yet. */
    PlayerStateSnapshot current(PlayerRef who);

    /**
     * Atomically replace {@code who}'s snapshot by applying {@code mutator} to the current one (or the initial
     * state when absent) and return the new value. The mutator must be a pure function of its input: it may
     * be retried under contention, and must not perform I/O or touch the live player.
     */
    PlayerStateSnapshot update(PlayerRef who, UnaryOperator<PlayerStateSnapshot> mutator);

    /** Drop {@code who}'s snapshot entirely; called on quit so a disconnected player holds no state. */
    void forget(PlayerRef who);
}
