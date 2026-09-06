package com.uxplima.uxmessentials.messaging.application;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /rtoggle}: flip whether the player takes part in reply routing. With reply routing off, a
 * {@code /reply} aimed at them is declined as if there were no fresh conversation, but a named {@code /msg}
 * still reaches them and mail still delivers: the toggle gates only the back-channel reply convenience. The
 * new state is reported to the player. The flag itself lives in the {@link ReplyRoutingStore} (per-holder PDC
 * state, mirroring {@code /msgtoggle}); this use case just flips it and renders the outcome.
 */
public final class ReplyToggle {

    private final ReplyRoutingStore replies;
    private final Notifier notifier;

    public ReplyToggle(ReplyRoutingStore replies, Notifier notifier) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Flip {@code who}'s reply-routing flag and tell them the new state. */
    public boolean toggle(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        boolean acceptsNow = replies.toggle(who);
        notifier.send(who, acceptsNow ? MessagingMessageKey.REPLY_TOGGLE_ON : MessagingMessageKey.REPLY_TOGGLE_OFF);
        return acceptsNow;
    }
}
