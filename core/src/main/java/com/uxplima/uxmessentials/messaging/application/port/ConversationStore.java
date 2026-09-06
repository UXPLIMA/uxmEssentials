package com.uxplima.uxmessentials.messaging.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the transient {@code /reply} targets. A private message is real-time and never
 * persisted, so this store holds only session state the messaging module drops on {@code stop()}. It records
 * each player's {@link LastConversation} (their last PM partner and when it was last touched), captured both
 * when they send and when they receive a message, so either side can {@code /reply}.
 *
 * <p>The in-memory adapter behind this port is a {@code ConcurrentHashMap} keyed by player uuid, safe to read
 * from the command thread and write from either side of a delivery. The reply-TTL rule lives on the
 * {@link LastConversation} aggregate; this port only stores and returns the latest entry.
 */
public interface ConversationStore {

    /** Record that {@code owner}'s most recent conversation is {@code conversation}, replacing any prior. */
    void remember(PlayerRef owner, LastConversation conversation);

    /** The owner's most recent conversation, if any has been recorded this session. */
    Optional<LastConversation> latest(PlayerRef owner);

    /** Forget the owner's reply target, e.g. on quit. */
    void forget(PlayerRef owner);
}
