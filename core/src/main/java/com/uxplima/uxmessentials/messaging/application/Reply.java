package com.uxplima.uxmessentials.messaging.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.messaging.application.port.ConversationStore;
import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /reply <text>}: answer the player's last conversation without naming the partner. It resolves the
 * reply target from the {@link ConversationStore} and enforces the reply-TTL rule the {@link LastConversation}
 * aggregate owns, a stale conversation (none ever, or the window elapsed) declines with
 * {@link MessagingError#NO_REPLY_TARGET} rather than misdirecting the reply to whoever the player last spoke
 * to long ago. A fresh-but-now-offline or vanished-and-unseeable partner resolves as offline, so a reply
 * never leaks that a vanished partner is still on the server.
 *
 * <p>Once the target is resolved and fresh, delivery and every downstream gate (mute, ignore, toggle,
 * socialspy, the both-sides reply capture, the event) are the {@link SendMessage} engine's job: this use
 * case only resolves the target and applies the TTL.
 */
public final class Reply {

    private final SendMessage sendMessage;
    private final ConversationStore conversations;
    private final PlayerLookup players;
    private final VanishVisibility vanish;
    private final ReplyRoutingStore replyRouting;
    private final Notifier notifier;
    private final Duration replyTtl;
    private final Clock clock;

    public Reply(
            SendMessage sendMessage,
            ConversationStore conversations,
            PlayerLookup players,
            VanishVisibility vanish,
            ReplyRoutingStore replyRouting,
            Notifier notifier,
            Duration replyTtl,
            Clock clock) {
        this.sendMessage = Objects.requireNonNull(sendMessage, "sendMessage");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.players = Objects.requireNonNull(players, "players");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.replyRouting = Objects.requireNonNull(replyRouting, "replyRouting");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.replyTtl = Objects.requireNonNull(replyTtl, "replyTtl");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Reply with {@code body} to {@code sender}'s last fresh conversation. */
    public Result<Unit, MessagingError> reply(PlayerRef sender, MessageBody body) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(body, "body");
        Optional<LastConversation> latest = conversations.latest(sender);
        if (latest.isEmpty() || !latest.get().isFresh(clock.instant(), replyTtl)) {
            return reject(sender);
        }
        return resolveAndSend(sender, latest.get().partner(), body);
    }

    private Result<Unit, MessagingError> resolveAndSend(PlayerRef sender, PlayerRef partner, MessageBody body) {
        Optional<PlayerRef> online = players.findByUuid(partner.uuid());
        if (online.isEmpty() || !players.isOnline(partner.uuid()) || vanish.isHiddenFrom(sender, partner)) {
            return reject(sender);
        }
        if (!replyRouting.acceptsReplies(online.get())) {
            // The partner turned reply routing off via /rtoggle. Decline as if there were no fresh
            // conversation, so the back-channel reply never leaks that they are on the server.
            return reject(sender);
        }
        return sendMessage.deliver(sender, online.get(), body);
    }

    private Result<Unit, MessagingError> reject(PlayerRef sender) {
        notifier.send(sender, MessagingError.NO_REPLY_TARGET.messageKey());
        return Result.err(MessagingError.NO_REPLY_TARGET);
    }
}
