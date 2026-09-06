package com.uxplima.uxmessentials.staff.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.domain.event.StaffChatSent;

/**
 * {@code /staffchat} (and {@code /sc}) {@code <message>}: send a line on the staff-only channel. The message
 * is fanned out to every online staff member through the soft-coupled {@link StaffChannel}, the same
 * staff-audience fan-out as {@code HelpOp.raise}, and the fact that it was sent is published as a domain
 * event for the audit trail. Staff chat is plain communication: it carries no sanction.
 *
 * <p>When the messaging module is disabled the channel binds {@link StaffChannel#NONE}, so the audience is
 * empty and the send degrades to silence rather than failing; the {@link StaffChatSent} event is still
 * published so the audit trail records the attempt.
 */
public final class SendStaffChat {

    private final StaffChannel channel;
    private final DomainEventPublisher events;

    public SendStaffChat(StaffChannel channel, DomainEventPublisher events) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Fan {@code message} from {@code from} out to the online staff audience. */
    public void send(PlayerRef from, String message) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(message, "message");
        List<PlayerRef> recipients = channel.onlineStaff();
        channel.send(from, recipients, message);
        events.publish(new StaffChatSent(from, message));
    }
}
