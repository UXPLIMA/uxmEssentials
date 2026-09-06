package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;

import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@link CommunicationPlaceholders} over the communication context's runtime state: the global {@link ChatLock}
 * the {@code /togglechat} command flips and the {@link BroadcastOptOutStore} the {@code /broadcasttoggle} command
 * flips. Built during communication wiring from the same instances those commands hold, so a placeholder matches
 * the live chat lock and the player's announcer subscription.
 *
 * <p>{@link #chatEnabled()} reports the inverse of the lock. Chat is open while it is not held. {@link
 * #receivesBroadcasts(PlayerRef)} reads the store's per-player bit; the store resolves the connected player, so
 * the read is meaningful for an online player.
 */
@NullMarked
public final class StoreCommunicationPlaceholders implements CommunicationPlaceholders {

    private final ChatLock chatLock;
    private final BroadcastOptOutStore optOutStore;

    public StoreCommunicationPlaceholders(ChatLock chatLock, BroadcastOptOutStore optOutStore) {
        this.chatLock = Objects.requireNonNull(chatLock, "chatLock");
        this.optOutStore = Objects.requireNonNull(optOutStore, "optOutStore");
    }

    @Override
    public boolean chatEnabled() {
        return !chatLock.isLocked();
    }

    @Override
    public boolean receivesBroadcasts(PlayerRef who) {
        return optOutStore.receivesBroadcasts(Objects.requireNonNull(who, "who"));
    }
}
