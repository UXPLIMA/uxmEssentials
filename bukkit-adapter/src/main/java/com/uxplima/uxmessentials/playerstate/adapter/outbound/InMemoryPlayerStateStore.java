package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The transient per-player state map backing {@link PlayerStateStore}: a
 * {@code ConcurrentHashMap<UUID, PlayerStateSnapshot>} mutated only through {@code compute} /
 * {@code computeIfAbsent}. The map holds immutable snapshots, so a reader on any region thread observes a
 * coherent value, and a concurrent toggle never loses an update. {@code compute} re-applies the pure mutator
 * under contention (docs/02-concurrency §the playerstate snapshot pattern).
 *
 * <p>Nothing here is persisted. A snapshot is seeded on first read (or on join), replaced by the toggle/set
 * use cases, and dropped on quit; a player who has never interacted has no entry and reads the neutral initial
 * state.
 */
@NullMarked
public final class InMemoryPlayerStateStore implements PlayerStateStore {

    private final ConcurrentHashMap<UUID, PlayerStateSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public PlayerStateSnapshot current(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return snapshots.computeIfAbsent(who.uuid(), id -> PlayerStateSnapshot.initial());
    }

    @Override
    public PlayerStateSnapshot update(PlayerRef who, UnaryOperator<PlayerStateSnapshot> mutator) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(mutator, "mutator");
        return snapshots.compute(who.uuid(), (id, existing) -> {
            PlayerStateSnapshot base = existing == null ? PlayerStateSnapshot.initial() : existing;
            return Objects.requireNonNull(mutator.apply(base), "mutator produced a null snapshot");
        });
    }

    @Override
    public void forget(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        snapshots.remove(who.uuid());
    }
}
