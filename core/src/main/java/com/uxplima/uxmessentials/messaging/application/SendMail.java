package com.uxplima.uxmessentials.messaging.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.domain.IgnoreChannel;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.messaging.domain.event.MailDelivered;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /mail send <player> <text>}: leave a piece of persistent, text-only mail. Mail is DB-backed and
 * survives restart (the hard messaging invariant). The recipient may be offline; the row waits and is read
 * on next join. The send is mute-gated (a muted sender cannot leave mail) and ignore-aware (a recipient who
 * ignores the sender silently drops the mail, the sender still seeing a sent confirmation so the ignore is
 * not observable, mirroring {@code /msg}).
 *
 * <p>On delivery it appends the item to the recipient's box (assigning its id), publishes
 * {@code MailDelivered}, and, when the recipient is online, notifies them of new mail subject to the
 * notify cooldown the adapter applies. There are no item attachments (out of scope); the body is the whole
 * payload.
 */
public final class SendMail {

    private final MailRepository mail;
    private final IgnoreStore ignores;
    private final MessageDelivery delivery;
    private final MutePolicy mute;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SendMail(
            MailRepository mail,
            IgnoreStore ignores,
            MessageDelivery delivery,
            MutePolicy mute,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.mail = Objects.requireNonNull(mail, "mail");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.mute = Objects.requireNonNull(mute, "mute");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Leave {@code body} as mail from {@code sender} to {@code recipient}. */
    public Result<Unit, MessagingError> send(PlayerRef sender, PlayerRef recipient, MessageBody body) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(body, "body");
        if (mute.isMuted(sender)) {
            notifier.send(sender, MessagingError.SENDER_MUTED.messageKey());
            return Result.err(MessagingError.SENDER_MUTED);
        }
        return persist(sender, recipient, body);
    }

    /**
     * Leave {@code body} as mail from the server itself, under {@code senderName}.
     *
     * <p>The path a plugin's mail takes. Nothing here can refuse it: there is no mute that applies to the server
     * and no way for a player to ignore it, so unlike {@link #send} it neither gates nor confirms, and the mail
     * lands whatever the recipient has turned off.
     */
    public void sendFromSystem(String senderName, PlayerRef recipient, MessageBody body) {
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(body, "body");
        deposit(recipient, MailSender.system(senderName), body);
    }

    private Result<Unit, MessagingError> persist(PlayerRef sender, PlayerRef recipient, MessageBody body) {
        confirm(sender, recipient);
        if (ignores.load(recipient).blocks(sender, IgnoreChannel.MAIL)) {
            return Result.ok(); // silently dropped; the sender already saw a sent confirmation
        }
        deposit(recipient, MailSender.player(sender), body);
        return Result.ok();
    }

    /** Store the item, announce it, and tell the recipient if they are here to be told. */
    private void deposit(PlayerRef recipient, MailSender from, MessageBody body) {
        MailItem stored = mail.append(MailItem.compose(recipient, from, body, clock.instant()));
        events.publish(new MailDelivered(recipient, stored.sender(), body, stored.sentAt()));
        delivery.notifyNewMail(recipient, stored);
    }

    private void confirm(PlayerRef sender, PlayerRef recipient) {
        notifier.send(sender, MessagingMessageKey.MAIL_SENT, Map.of("player", recipient.name()));
    }
}
