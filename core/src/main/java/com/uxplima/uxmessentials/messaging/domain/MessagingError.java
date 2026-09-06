package com.uxplima.uxmessentials.messaging.domain;

import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;

/**
 * The modelled failures a messaging operation can produce. Each value carries the
 * {@link MessagingMessageKey} the command adapter renders, so a use case returns
 * {@code Result.err(MessagingError.X)} and the caller never re-derives the message. The error carries it,
 * and the failure reason and its localized text never drift apart (mirroring the homes context's
 * {@code HomeError}).
 */
public enum MessagingError {

    /** {@code /msg} or {@code /reply} to a player who is offline or unknown. */
    TARGET_OFFLINE(MessagingMessageKey.MSG_TARGET_OFFLINE),

    /** A vanished target the sender cannot see, resolved as if offline (vanish-aware resolution). */
    TARGET_HIDDEN(MessagingMessageKey.MSG_TARGET_OFFLINE),

    /** {@code /msg} or {@code /reply} addressed to the sender themselves. */
    SELF(MessagingMessageKey.MSG_SELF),

    /** The target has {@code /msgtoggle}d incoming private messages off. */
    TARGET_TOGGLED_OFF(MessagingMessageKey.MSG_TOGGLED_OFF),

    /** The sender is muted by the moderation context: outbound messaging is gated. */
    SENDER_MUTED(MessagingMessageKey.MSG_MUTED),

    /** The target ignores the sender; delivery is silently declined (rendered as delivered to the sender). */
    IGNORED(MessagingMessageKey.MSG_IGNORED),

    /** {@code /reply} with no fresh conversation to answer (none ever, or the reply-TTL elapsed). */
    NO_REPLY_TARGET(MessagingMessageKey.MSG_NO_REPLY_TARGET),

    /** {@code /ignore} of the sender themselves. */
    IGNORE_SELF(MessagingMessageKey.IGNORE_SELF),

    /** {@code /unignore} of a player the owner does not ignore. */
    NOT_IGNORED(MessagingMessageKey.IGNORE_NOT_IGNORED),

    /** {@code /mail read} or {@code /mail clear} on an empty mailbox. */
    MAILBOX_EMPTY(MessagingMessageKey.MAIL_EMPTY);

    private final MessagingMessageKey messageKey;

    MessagingError(MessagingMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public MessagingMessageKey messageKey() {
        return messageKey;
    }
}
