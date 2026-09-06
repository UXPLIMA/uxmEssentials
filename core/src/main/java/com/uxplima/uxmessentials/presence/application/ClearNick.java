package com.uxplima.uxmessentials.presence.application;

import java.util.Objects;

import com.uxplima.uxmessentials.presence.application.port.NickStore;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /nick off}: clear a player's display name and restore their account name. Removes the
 * {@link NickStore} stamp and confirms with {@link PresenceMessageKey#NICK_CLEARED}. Clearing is idempotent
 * clearing a player who has no nick still restores the account name and confirms, so the command never has to
 * branch on whether a nick was set.
 */
public final class ClearNick {

    private final NickStore store;
    private final Notifier notifier;

    public ClearNick(NickStore store, Notifier notifier) {
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Clear {@code who}'s nick and confirm to them. */
    public void clear(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        store.clearNick(who);
        notifier.send(who, PresenceMessageKey.NICK_CLEARED);
    }
}
