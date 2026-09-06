package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player powertool on/off switch ({@code /powertooltoggle}). Bindings stamped onto items stay put; this
 * toggle only governs whether the interact listener acts on them, so a player can silence their bound items
 * without re-binding each one. Default is on: a freshly-bound item fires.
 *
 * <p>State is transient in-memory runtime state: a concurrent set of the UUIDs that have toggled <em>off</em>,
 * keyed by player so the interact listener's read is a hot-path-safe membership check. The module's {@code
 * stop()} drops this with the wiring: there is nothing to persist (the binding itself lives in item PDC).
 */
@NullMarked
public final class PowertoolToggleStore {

    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();

    /** Flip {@code who}'s powertool state and return the new enabled value ({@code true} = bindings fire). */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (disabled.remove(who.uuid())) {
            return true;
        }
        disabled.add(who.uuid());
        return false;
    }

    /** Whether {@code who}'s powertool bindings currently fire on interact (default {@code true}). */
    public boolean enabled(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return !disabled.contains(who.uuid());
    }

    /** Forget {@code who}'s toggle state: called on quit so a relog starts from the default. */
    public void forget(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        disabled.remove(who.uuid());
    }
}
