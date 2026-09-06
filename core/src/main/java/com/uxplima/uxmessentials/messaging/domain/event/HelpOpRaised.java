package com.uxplima.uxmessentials.messaging.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player raised a {@code /helpop} support request. Raised after mute-gating passes; the inbound adapter
 * subscribes to fan the request out to every online staff member holding the receive node, and other
 * plugins (a ticketing bridge) may observe it. The request itself is transient, like a private message,
 * only the fact that it was raised is a domain event.
 *
 * @param requester the player asking for help
 * @param body the request text
 * @param raisedAt when the request was raised
 */
public record HelpOpRaised(PlayerRef requester, MessageBody body, Instant raisedAt) implements MessagingEvent {

    public HelpOpRaised {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(raisedAt, "raisedAt");
    }
}
