package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.messaging.application.port.ConversationStore;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link ConversationStore} implementation: each player's {@code /reply} target. A private message is
 * real-time and never persisted, so this is session state held in-memory and dropped on {@code stop()} via
 * {@link #clear()}; a player's reply target is forgotten on quit by the inbound listener. The reply-TTL rule
 * lives on the {@link LastConversation} aggregate: this store only keeps the latest entry per owner.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The map is a {@link ConcurrentHashMap} keyed by owner uuid,
 * written from both sides of a delivery (the {@code SendMessage} engine records sender and target) and read
 * from the {@code /reply} command thread.
 */
@NullMarked
public final class InMemoryConversationStore implements ConversationStore {

    private final ConcurrentHashMap<UUID, LastConversation> latest = new ConcurrentHashMap<>();

    @Override
    public void remember(PlayerRef owner, LastConversation conversation) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(conversation, "conversation");
        latest.put(owner.uuid(), conversation);
    }

    @Override
    public Optional<LastConversation> latest(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        return Optional.ofNullable(latest.get(owner.uuid()));
    }

    @Override
    public void forget(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        latest.remove(owner.uuid());
    }

    /** Drop every reply target on module stop. */
    public void clear() {
        latest.clear();
    }
}
