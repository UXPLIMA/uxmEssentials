package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import org.jspecify.annotations.NullMarked;

/**
 * The messaging-backed {@link StaffChannel}: it resolves the staff-chat audience through the messaging
 * context's {@link StaffAudience} (every online holder of the staff-chat node) and fans each line out through
 * the shared {@link Messages}/{@link MessageSink} pair, formatting it with {@code STAFF_CHAT_FORMAT} in each
 * recipient's locale, the same staff-audience fan-out shape as {@code HelpOp}.
 *
 * <p>This impl is bound only when the messaging module is enabled; with messaging off the wiring binds
 * {@link StaffChannel#NONE}, so the audience is empty and a send degrades to silence rather than failing.
 */
@NullMarked
public final class MessagingStaffChannel implements StaffChannel {

    private final StaffAudience audience;
    private final Messages messages;
    private final MessageSink sink;
    private final String staffChatNode;

    public MessagingStaffChannel(StaffAudience audience, Messages messages, MessageSink sink, String staffChatNode) {
        this.audience = Objects.requireNonNull(audience, "audience");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.staffChatNode = Objects.requireNonNull(staffChatNode, "staffChatNode");
    }

    @Override
    public void send(PlayerRef from, List<PlayerRef> recipients, String message) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(recipients, "recipients");
        Objects.requireNonNull(message, "message");
        Map<String, String> placeholders = Map.of("player", from.name(), "message", message);
        for (PlayerRef viewer : recipients) {
            sink.deliver(viewer, messages.resolve(viewer, StaffMessageKey.STAFF_CHAT_FORMAT, placeholders));
        }
    }

    @Override
    public List<PlayerRef> onlineStaff() {
        return audience.onlineWith(staffChatNode);
    }
}
