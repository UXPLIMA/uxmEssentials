package com.uxplima.uxmessentials.messaging.application.port;

import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port that renders and delivers the messaging context's player-facing lines to a viewer. It pairs
 * the kernel {@code Messages} resolution with the {@code MessageSink} delivery for the messaging-specific
 * shapes (a private message has both a sender-echo and a recipient line, a help-op fans out to staff) that
 * a generic notifier cannot express in one call.
 *
 * <p>Every line resolves a {@link com.uxplima.uxmessentials.messaging.application.MessagingMessageKey} in the
 * viewer's locale and hops to their region thread, silently no-opping when the viewer is offline (mail still
 * persists for an offline recipient, see {@link MailRepository}). The message body is passed as a literal
 * placeholder value; whether MiniMessage tags in it are parsed depends on the {@code uxmessentials.msg.color}
 * node, which the adapter resolves.
 */
public interface MessageDelivery {

    /** Deliver the recipient-side line of a private message from {@code sender} to {@code recipient}. */
    void deliverMessage(PlayerRef sender, PlayerRef recipient, MessageBody body);

    /** Echo a sent private message back to its {@code sender} (the "to <recipient>" confirmation line). */
    void echoSent(PlayerRef sender, PlayerRef recipient, MessageBody body);

    /** Notify an online {@code recipient} that new {@code item} mail arrived (subject to the notify cooldown). */
    void notifyNewMail(PlayerRef recipient, MailItem item);

    /** Render one mail item to its reader during {@code /mail read}. */
    void deliverMailLine(PlayerRef reader, MailItem item);

    /** Fan a {@code /helpop} request from {@code requester} out to one staff {@code viewer}. */
    void deliverHelpOp(PlayerRef requester, PlayerRef viewer, MessageBody body);

    /** Show a socialspy {@code observer} a private message between {@code sender} and {@code recipient}. */
    void deliverSpy(PlayerRef observer, PlayerRef sender, PlayerRef recipient, MessageBody body);
}
