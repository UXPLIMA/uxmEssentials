package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.port.AfkStatus;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The playerstate {@link AfkStatus} implementation, soft-coupled to the presence context through its in-memory
 * {@link PresenceStore}, the same store the AFK sweep, the vanish listeners, and the {@code afk} placeholder
 * already read. The playtime sampler asks this whether each online player is AFK so it credits the sample interval
 * to the active or the AFK column; the read is via {@link PresenceStore#current}, which never mutates the map.
 *
 * <p>This is only constructed and bound into the sampler (through the rebindable {@code MutablePlaytimeAfkStatus}
 * holder) when the presence module wires; presence wires after playerstate, so the binding lands through the
 * holder. When presence is disabled the holder stays on {@link AfkStatus#NEVER} and every sample counts as active.
 */
@NullMarked
public final class PresenceAfkStatus implements AfkStatus {

    private final PresenceStore store;

    public PresenceAfkStatus(PresenceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public boolean isAfk(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return store.current(who).afk();
    }
}
