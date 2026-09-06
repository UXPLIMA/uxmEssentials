package com.uxplima.uxmessentials.messaging.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A private message was delivered from {@code sender} to {@code recipient}. Raised on a successful
 * {@code /msg} or {@code /reply}; the socialspy audit path subscribes so staff can observe the message
 * without being in the conversation. A message blocked by an ignore, a toggle, or a mute raises nothing
 * only a delivered message is a fact.
 *
 * @param sender who sent the message
 * @param recipient who received it
 * @param body the message text
 * @param sentAt when it was delivered
 */
public record PrivateMessageSent(PlayerRef sender, PlayerRef recipient, MessageBody body, Instant sentAt)
        implements MessagingEvent {

    public PrivateMessageSent {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(sentAt, "sentAt");
    }
}
