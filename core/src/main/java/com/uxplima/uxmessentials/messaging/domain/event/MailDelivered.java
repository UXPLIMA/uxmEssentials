package com.uxplima.uxmessentials.messaging.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A piece of mail was delivered into {@code recipient}'s mailbox. Raised on a successful {@code /mail send}
 * after the row is written; offline delivery still raises it, and the inbound adapter uses it (together with
 * a notify cooldown) to ping an online recipient about new mail. Mail is text-only. There are no item
 * attachments, so the body is the whole payload.
 *
 * @param recipient who the mail is for
 * @param sender who the mail is from
 * @param body the text payload
 * @param sentAt when it was delivered
 */
public record MailDelivered(PlayerRef recipient, MailSender sender, MessageBody body, Instant sentAt)
        implements MessagingEvent {

    public MailDelivered {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(sentAt, "sentAt");
    }
}
