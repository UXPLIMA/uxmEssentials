package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.messaging.application.port.AfkStatus;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link AfkStatus} implementation, soft-coupled to the presence context through its in-memory
 * {@link PresenceStore}, the same store the AFK sweep, the vanish listeners, and the {@code afk} placeholder
 * already read. Messaging asks this whether a {@code /msg} target is away so the sender gets the courtesy AFK
 * notice; the read is via {@link PresenceStore#current}, which never mutates the map.
 *
 * <p>An AFK player with a reason returns that reason; an AFK player who never set one (auto-AFK) returns a
 * present-but-blank value, so the caller can tell "AFK, no reason" apart from "not AFK", the contract the
 * {@link AfkStatus} port states. A player who is not AFK returns empty.
 *
 * <p>The coupling degrades exactly like the mute soft-couple: this adapter is only constructed and bound into
 * messaging when the presence module wires (presence wires after messaging, so the binding lands through the
 * rebindable {@code MutableAfkStatus} holder). When presence is disabled the holder stays on
 * {@link AfkStatus#NEVER} and messaging sees "no one is AFK". No messaging-side branch, mirroring how the
 * {@code MutableMutePolicy} degrades against moderation.
 */
@NullMarked
public final class PresenceAfkStatus implements AfkStatus {

    private final PresenceStore store;

    public PresenceAfkStatus(PresenceStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<String> afkReasonOf(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        PlayerPresence presence = store.current(who);
        if (!presence.afk()) {
            return Optional.empty();
        }
        // AFK with no reason set (auto-AFK) is still "AFK": surface a present-but-blank value so the caller
        // distinguishes it from "not AFK", per the AfkStatus contract.
        return Optional.of(presence.afkReason().orElse(""));
    }
}
