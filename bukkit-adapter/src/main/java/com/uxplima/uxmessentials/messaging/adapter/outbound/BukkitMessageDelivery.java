package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link MessageDelivery} implementation: it resolves each messaging line through the kernel
 * {@link Messages} catalog in the viewer's locale and delivers it through the {@link MessageSink} (which
 * hops to the viewer's region thread and silently no-ops when offline). The message body is passed as a
 * literal {@code {message}} placeholder; the catalog template decides the framing ("to X", "from X"), and
 * whether MiniMessage tags inside the body render is governed upstream by the {@code uxmessentials.msg.color}
 * node: this adapter passes the body through as the sender supplied it.
 */
@NullMarked
public final class BukkitMessageDelivery implements MessageDelivery {

    private static final DateTimeFormatter SENT_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Messages messages;
    private final MessageSink sink;

    public BukkitMessageDelivery(Messages messages, MessageSink sink) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public void deliverMessage(PlayerRef sender, PlayerRef recipient, MessageBody body) {
        send(recipient, MessagingMessageKey.MSG_RECEIVED, two("player", sender.name(), "message", body.value()));
    }

    @Override
    public void echoSent(PlayerRef sender, PlayerRef recipient, MessageBody body) {
        send(sender, MessagingMessageKey.MSG_SENT, two("player", recipient.name(), "message", body.value()));
    }

    @Override
    public void notifyNewMail(PlayerRef recipient, MailItem item) {
        send(
                recipient,
                MessagingMessageKey.MAIL_NEW_NOTIFY,
                Map.of("player", item.sender().name()));
    }

    @Override
    public void deliverMailLine(PlayerRef reader, MailItem item) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", item.sender().name());
        placeholders.put("message", item.body().value());
        placeholders.put("sent", format(item.sentAt()));
        send(reader, MessagingMessageKey.MAIL_READ_ENTRY, placeholders);
    }

    @Override
    public void deliverHelpOp(PlayerRef requester, PlayerRef viewer, MessageBody body) {
        send(viewer, MessagingMessageKey.HELPOP_RECEIVED, two("player", requester.name(), "message", body.value()));
    }

    @Override
    public void deliverSpy(PlayerRef observer, PlayerRef sender, PlayerRef recipient, MessageBody body) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("from", sender.name());
        placeholders.put("to", recipient.name());
        placeholders.put("message", body.value());
        send(observer, MessagingMessageKey.MSG_SPY, placeholders);
    }

    private void send(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        sink.deliver(viewer, messages.resolve(viewer, key, placeholders));
    }

    private static Map<String, String> two(String k1, String v1, String k2, String v2) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(k1, v1);
        placeholders.put(k2, v2);
        return placeholders;
    }

    private static String format(Instant at) {
        return SENT_AT.format(at);
    }
}
