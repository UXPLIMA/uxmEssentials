package com.uxplima.uxmessentials.messaging.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /mail read}: show the reader their persistent mailbox newest-first and mark every item read. An
 * empty box reports {@link MessagingError#MAILBOX_EMPTY} rather than rendering a header with no entries.
 * Reading is a one-shot side-effect, the header, one line per item, then a single {@code markAllRead} so a
 * second {@code /mail read} shows the same mail with no unread flag and no fresh notify.
 */
public final class ReadMail {

    private final MailRepository mail;
    private final MessageDelivery delivery;
    private final Notifier notifier;

    public ReadMail(MailRepository mail, MessageDelivery delivery, Notifier notifier) {
        this.mail = Objects.requireNonNull(mail, "mail");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render {@code reader}'s mailbox and mark it read. */
    public Result<Unit, MessagingError> read(PlayerRef reader) {
        Objects.requireNonNull(reader, "reader");
        MailBox box = mail.load(reader);
        if (box.isEmpty()) {
            notifier.send(reader, MessagingError.MAILBOX_EMPTY.messageKey());
            return Result.err(MessagingError.MAILBOX_EMPTY);
        }
        render(reader, box);
        mail.markAllRead(reader);
        return Result.ok();
    }

    private void render(PlayerRef reader, MailBox box) {
        notifier.send(reader, MessagingMessageKey.MAIL_READ_HEADER, Map.of("count", Integer.toString(box.size())));
        for (MailItem item : box.items()) {
            delivery.deliverMailLine(reader, item);
        }
    }
}
