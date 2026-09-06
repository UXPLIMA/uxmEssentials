package com.uxplima.uxmessentials.presence.adapter.outbound;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The transient per-player presence map backing {@link PresenceStore}: a
 * {@code ConcurrentHashMap<UUID, PlayerPresence>} mutated only through {@code compute} / {@code computeIfAbsent}.
 * The map holds immutable aggregates, so a sync activity listener, the async AFK sweep, and a cross-context
 * vanish lookup all observe a coherent value, and a concurrent transition never loses an update
 * {@code compute} re-applies the pure mutator under contention (docs/02-concurrency.md §6.9).
 *
 * <p>Nothing here is persisted. A presence is seeded on first read (or on join), re-stamped by the activity
 * listeners, flipped by the AFK use cases and the sweep, and dropped on quit. The {@code PlayerRef} key
 * is kept alongside the aggregate so {@link #snapshotAll} can hand the sweep both without a second lookup; the
 * map itself is keyed by UUID for identity-correct {@code compute}.
 *
 * <p><b>Vanish is not owned here.</b> Presence stopped tracking its own vanish flag when vanish became its own
 * context and single authority. So the presence map never writes {@code vanished}; instead {@link #current} overlays
 * the vanished bit from the injected {@code vanishLookup} (the vanish authority's {@code isVanished}, or always-false
 * when the vanish module is disabled) so presence's own vanish-aware readers. The {@code %..._vanished%} placeholder,
 * the sleep exclusion, the settings panel, reflect the one vanish state without a second flag to keep in sync.
 */
@NullMarked
public final class InMemoryPresenceStore implements PresenceStore {

    private final ConcurrentHashMap<UUID, Entry> presences = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Predicate<UUID> vanishLookup;

    public InMemoryPresenceStore(Clock clock, Predicate<UUID> vanishLookup) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.vanishLookup = Objects.requireNonNull(vanishLookup, "vanishLookup");
    }

    @Override
    public PlayerPresence current(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        PlayerPresence base = presences
                .computeIfAbsent(who.uuid(), id -> new Entry(who, PlayerPresence.active(clock.instant())))
                .presence();
        // Presence does not own the vanished bit; overlay it from the vanish authority so a presence reader sees the
        // one vanish state. The stored aggregate's vanished flag is never set, so this is the only source of truth.
        return vanishLookup.test(who.uuid()) ? base.vanish() : base;
    }

    @Override
    public PlayerPresence update(PlayerRef who, UnaryOperator<PlayerPresence> mutator) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(mutator, "mutator");
        return presences
                .compute(who.uuid(), (id, existing) -> {
                    PlayerPresence base =
                            existing == null ? PlayerPresence.active(clock.instant()) : existing.presence();
                    return new Entry(
                            who, Objects.requireNonNull(mutator.apply(base), "mutator produced a null presence"));
                })
                .presence();
    }

    @Override
    public void forget(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        presences.remove(who.uuid());
    }

    @Override
    public Map<PlayerRef, PlayerPresence> snapshotAll() {
        Map<PlayerRef, PlayerPresence> copy = new HashMap<>();
        presences.values().forEach(entry -> copy.put(entry.who(), entry.presence()));
        return Map.copyOf(copy);
    }

    /** Drop every tracked presence; called on module stop so a disabled context holds zero runtime state. */
    public void clear() {
        presences.clear();
    }

    /** The map value: the player's ref paired with their immutable presence, so the sweep gets both at once. */
    private record Entry(PlayerRef who, PlayerPresence presence) {}
}
