package com.uxplima.uxmessentials.moderation.application.port;

import java.util.Collection;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the live-player side of a sanction. The things a use case must do to a connected player
 * that are not DB writes: kick them, freeze/unfreeze movement, teleport them into a jail, and enumerate the
 * online players a {@code /kickall} clears. The adapter maps each to the live {@code Player} and silently
 * no-ops on an offline target (an offline {@code /jail} is just a DB write; the join listener re-applies it
 * on reconnect).
 *
 * <p>The freeze state is session-scoped runtime state the adapter owns (movement-cancel via a move listener),
 * not a DB-backed sanction: a relog clears a freeze. The jail teleport hops to the entity's region thread
 * through the kernel {@code Scheduler}, so it is Folia-valid.
 */
public interface Sanctions {

    /** Kick {@code target} with the rendered reason for {@code reasonKey}; no-op when offline. */
    void kick(PlayerRef target, MessageKey reasonKey, String reasonText);

    /** Every currently-connected player, for a {@code /kickall} fan-out. */
    Collection<PlayerRef> onlinePlayers();

    /** Pin {@code target} in place (movement cancelled) until {@link #unfreeze}; no-op when offline. */
    void freeze(PlayerRef target);

    /** Release a frozen {@code target}; no-op when not frozen or offline. */
    void unfreeze(PlayerRef target);

    /** True when {@code target} is currently frozen. */
    boolean isFrozen(PlayerRef target);

    /** Teleport {@code target} into the named jail; no-op when offline or the jail is unresolved. */
    void sendToJail(PlayerRef target, String jail);

    /** Release {@code target} from a jail back to spawn; no-op when offline. */
    void releaseFromJail(PlayerRef target);
}
