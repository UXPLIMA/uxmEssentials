package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player {@code /unlimited} switch: who has unlimited block placement on. Default is off. When on, the
 * block-place refill listener tops the placed stack back up so the player never runs out, the standard
 * {@code /unlimited} behaviour (docs/10-feature-modules.md §15.10, mob/entity group).
 *
 * <p>State is transient in-memory runtime state: a concurrent set of the UUIDs that have toggled <em>on</em>,
 * keyed by player so the place listener's read is a hot-path-safe membership check. The module's {@code stop()}
 * drops this with the wiring; quit forgets the player so a relog starts off.
 */
@NullMarked
public final class UnlimitedPlacementStore {

    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    /** Flip {@code who}'s unlimited state and return the new value ({@code true} = placement is refilled). */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (enabled.remove(who.uuid())) {
            return false;
        }
        enabled.add(who.uuid());
        return true;
    }

    /** Whether {@code who} currently has unlimited block placement (default {@code false}). */
    public boolean enabled(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return enabled.contains(who.uuid());
    }

    /** Forget {@code who}'s state: called on quit so a relog starts off. */
    public void forget(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        enabled.remove(who.uuid());
    }
}
