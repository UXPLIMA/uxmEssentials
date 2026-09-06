package com.uxplima.uxmessentials.messaging.application;

import java.time.Clock;
import java.util.Collection;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /mail sendall <text>}: broadcast one piece of mail to a set of recipients. Each recipient gets their
 * own durable, DB-backed mail row (the hard messaging invariant) from {@code sender}, so an offline player
 * still reads it on next join, the same {@link MailRepository#append} path {@link SendMail} uses, fanned out.
 *
 * <p>The recipient set is supplied by the command adapter (its scope. Online players, all known mailbox
 * owners: is the adapter's decision and is documented there). This use case does not gate on ignore or mute:
 * a broadcast is a staff/operator action behind its own permission node, not a normal player message, so the
 * per-recipient ignore filter that {@link SendMail} applies is intentionally absent here. The {@code sender}
 * is not auto-excluded from {@code recipients}; the caller decides whether the broadcaster receives a copy.
 */
public final class SendMailToAll {

    private final MailRepository mail;
    private final Clock clock;

    public SendMailToAll(MailRepository mail, Clock clock) {
        this.mail = Objects.requireNonNull(mail, "mail");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Store one mail from {@code sender} carrying {@code body} for every player in {@code recipients},
     * returning how many were stored. An empty recipient set stores nothing and returns {@code 0}.
     */
    public int sendToAll(PlayerRef sender, MessageBody body, Collection<PlayerRef> recipients) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(recipients, "recipients");
        MailSender from = MailSender.player(sender);
        int stored = 0;
        for (PlayerRef recipient : recipients) {
            Objects.requireNonNull(recipient, "recipient");
            mail.append(MailItem.compose(recipient, from, body, clock.instant()));
            stored++;
        }
        return stored;
    }
}
