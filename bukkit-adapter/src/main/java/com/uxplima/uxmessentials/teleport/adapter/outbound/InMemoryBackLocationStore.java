package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.BackLocationStore;
import com.uxplima.uxmessentials.teleport.domain.BackLocation;
import org.jspecify.annotations.NullMarked;

/**
 * In-memory {@link BackLocationStore}: each player's current {@code /back} return point, replaced on
 * every capture. A {@code /back} point is transient session state (never PDC-stamped, never DB-backed)
 * so it lives in a per-player map the teleport module drops on {@code stop()} via {@link #clear()} and
 * re-arms on the next capture (a relog clears it).
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. A {@link ConcurrentHashMap} keyed by player uuid; capture and
 * read are independent map operations safe from any thread.
 */
@NullMarked
public final class InMemoryBackLocationStore implements BackLocationStore {

    private final ConcurrentHashMap<UUID, BackLocation> captures = new ConcurrentHashMap<>();

    @Override
    public void capture(PlayerRef who, BackLocation location) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(location, "location");
        captures.put(who.uuid(), location);
    }

    @Override
    public Optional<BackLocation> current(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return Optional.ofNullable(captures.get(who.uuid()));
    }

    @Override
    public void clear(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        captures.remove(who.uuid());
    }

    /** Drop every capture on module stop. */
    public void clear() {
        captures.clear();
    }
}
