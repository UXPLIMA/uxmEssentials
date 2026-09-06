package com.uxplima.uxmessentials.security.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NullMarked;

/**
 * The transient, per-session record of the client brand each online player reported, so {@code /clientinfo} can
 * report it. A brand is session state (a reconnecting player re-reports it), so this is in-memory and never
 * persisted; the entry is dropped on quit and the whole map is cleared on module stop, leaving no residue after a
 * disable or reload.
 *
 * <p>Keyed by player UUID and mutated only through the concurrent map's atomic primitives, so the join listener
 * (which records) and the {@code /clientinfo} command (which reads) never need a lock.
 */
@NullMarked
public final class ClientBrandRegistry {

    private final ConcurrentHashMap<UUID, String> brands = new ConcurrentHashMap<>();

    /** Remember the {@code brand} the player with {@code playerId} reported. */
    public void record(UUID playerId, String brand) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(brand, "brand");
        brands.put(playerId, brand);
    }

    /** The brand recorded for {@code playerId} this session, or empty when none has been seen. */
    public Optional<String> brandOf(UUID playerId) {
        return Optional.ofNullable(brands.get(Objects.requireNonNull(playerId, "playerId")));
    }

    /** Drop the entry for a leaving player. */
    public void clear(UUID playerId) {
        brands.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Drop every recorded brand: called on module stop so a disable leaves nothing behind. */
    public void clearAll() {
        brands.clear();
    }
}
