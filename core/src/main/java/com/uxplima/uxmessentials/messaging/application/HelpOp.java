package com.uxplima.uxmessentials.messaging.application;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.messaging.domain.event.HelpOpRaised;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /helpop <text>}: open a player→staff support request. The send is mute-gated (a muted player cannot
 * raise a help-op) and fans the request out to every online staff member holding the receive node. The
 * requester always gets a sent confirmation even when no staff are online, so a help-op is never lost
 * silently: operators can read the {@code HelpOpRaised} event in their audit trail. The request itself is
 * transient; only the fact that it was raised is a domain event.
 */
public final class HelpOp {

    private static final String RECEIVE_NODE = "uxmessentials.helpop.receive";

    private final StaffAudience staff;
    private final MessageDelivery delivery;
    private final MutePolicy mute;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public HelpOp(
            StaffAudience staff,
            MessageDelivery delivery,
            MutePolicy mute,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.staff = Objects.requireNonNull(staff, "staff");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.mute = Objects.requireNonNull(mute, "mute");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Raise a help-op from {@code requester} with {@code body}. */
    public Result<Unit, MessagingError> raise(PlayerRef requester, MessageBody body) {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(body, "body");
        if (mute.isMuted(requester)) {
            notifier.send(requester, MessagingError.SENDER_MUTED.messageKey());
            return Result.err(MessagingError.SENDER_MUTED);
        }
        fanOut(requester, body);
        events.publish(new HelpOpRaised(requester, body, clock.instant()));
        return Result.ok();
    }

    private void fanOut(PlayerRef requester, MessageBody body) {
        List<PlayerRef> recipients = staff.onlineWith(RECEIVE_NODE);
        for (PlayerRef viewer : recipients) {
            delivery.deliverHelpOp(requester, viewer, body);
        }
        notifier.send(
                requester,
                recipients.isEmpty() ? MessagingMessageKey.HELPOP_NO_STAFF : MessagingMessageKey.HELPOP_SENT);
    }
}
