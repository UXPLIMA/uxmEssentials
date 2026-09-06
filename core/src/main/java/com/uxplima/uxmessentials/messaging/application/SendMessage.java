package com.uxplima.uxmessentials.messaging.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.messaging.application.port.AfkStatus;
import com.uxplima.uxmessentials.messaging.application.port.ConversationStore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreChannel;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.messaging.domain.event.PrivateMessageSent;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /msg <player> <text>}: send a private message, applying every delivery gate in order. The target is
 * already resolved by the command adapter, whose lookup is vanish-aware (a vanished target the sender cannot
 * see is handed to this use case as offline, {@code targetOnline=false}, so its presence is never leaked,
 * the same {@code canSee} seam the teleport context applies to {@code /tpa}). This use case then gates on the
 * online branch on mute (the moderation soft-couple), self,
 * toggle, and ignore: a target who toggled DMs off rejects with a visible reason; a target who ignores the
 * sender silently declines. The sender's echo still says delivered, so an ignore is not observable,
 * matching the ignore-aware contract.
 *
 * <p>On delivery it echoes to the sender, delivers to the recipient, fans out to active socialspy staff,
 * records the reply target on <em>both</em> sides (either may {@code /reply}), and publishes
 * {@code PrivateMessageSent}. The reply path ({@link Reply}) shares this engine through {@link #deliver}.
 *
 * <p>Two presence-aware courtesies wrap that core flow. <strong>AFK notice:</strong> after a real delivery to
 * an AFK target the sender is also told the target is away (the {@link AfkStatus} soft-couple), so they know
 * not to expect a reply: AFK is a notice, never a block, and the message still delivers. The notice fires
 * only on a genuine delivery, never on a silently-dropped ignore: telling the sender "they're AFK" there
 * would leak that the (ignoring) target is online, breaking the ignore-is-not-observable contract.
 * <strong>Offline → mail fallback:</strong> when the caller reports the target is offline and the
 * {@code offlineToMail} policy is on, the message is stored as durable mail (the {@link MailRepository}
 * append path {@link SendMail} uses) and the sender is told it became mail; with the policy off the existing
 * {@code TARGET_OFFLINE} rejection stands.
 *
 * <p><strong>Vanish privacy is untouched.</strong> A vanished target the sender cannot see is routed by the
 * command adapter ({@code MsgCommand}) through the same {@code targetOnline=false} offline path as a genuinely
 * offline player, so this use case treats it exactly as offline: it stores mail when {@code offlineToMail} is
 * on (the note waits in the vanished player's box) and renders the {@code TARGET_OFFLINE} rejection when it is
 * off. A hidden target never takes the live delivery branch, so their presence is never leaked and the sender's
 * feedback is byte-identical to that for a real offline target in both config modes.
 */
public final class SendMessage {

    private final MessageDelivery delivery;
    private final IgnoreStore ignores;
    private final ConversationStore conversations;
    private final MessageToggleStore toggles;
    private final SocialSpyStore socialSpy;
    private final MutePolicy mute;
    private final AfkStatus afk;
    private final MailRepository mail;
    private final boolean offlineToMail;
    private final Notifier notifier;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SendMessage(
            MessageDelivery delivery,
            IgnoreStore ignores,
            ConversationStore conversations,
            MessageToggleStore toggles,
            SocialSpyStore socialSpy,
            MutePolicy mute,
            AfkStatus afk,
            MailRepository mail,
            boolean offlineToMail,
            Notifier notifier,
            DomainEventPublisher events,
            Clock clock) {
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.ignores = Objects.requireNonNull(ignores, "ignores");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.socialSpy = Objects.requireNonNull(socialSpy, "socialSpy");
        this.mute = Objects.requireNonNull(mute, "mute");
        this.afk = Objects.requireNonNull(afk, "afk");
        this.mail = Objects.requireNonNull(mail, "mail");
        this.offlineToMail = offlineToMail;
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Send {@code body} from {@code sender} to an online {@code target}, applying every gate. Convenience for
     * the common online case (and the {@link Reply} path, whose lookup only resolves online targets), equal
     * to {@link #send(PlayerRef, PlayerRef, MessageBody, boolean)} with {@code targetOnline=true}.
     */
    public Result<Unit, MessagingError> send(PlayerRef sender, PlayerRef target, MessageBody body) {
        return send(sender, target, body, true);
    }

    /**
     * Send {@code body} from {@code sender} to {@code target}, applying every gate. When {@code targetOnline}
     * is false the target is genuinely offline: with the offline-to-mail policy on the message is stored as
     * mail and the sender is told; with it off the {@code TARGET_OFFLINE} rejection stands. (A vanished,
     * unseeable target is routed here on this same {@code targetOnline=false} branch, so it is handled exactly
     * as a genuinely-offline target: see the class note on vanish privacy.)
     */
    public Result<Unit, MessagingError> send(
            PlayerRef sender, PlayerRef target, MessageBody body, boolean targetOnline) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(body, "body");
        if (!targetOnline) {
            return offline(sender, target, body);
        }
        return deliver(sender, target, body);
    }

    private Result<Unit, MessagingError> offline(PlayerRef sender, PlayerRef target, MessageBody body) {
        // A muted sender is gated even on the mail fallback, mirroring SendMail's mute gate.
        if (mute.isMuted(sender)) {
            return reject(sender, MessagingError.SENDER_MUTED);
        }
        if (!offlineToMail) {
            return reject(sender, MessagingError.TARGET_OFFLINE);
        }
        mail.append(MailItem.compose(target, MailSender.player(sender), body, clock.instant()));
        notifier.send(sender, MessagingMessageKey.MSG_SENT_TO_MAIL, Map.of("player", target.name()));
        return Result.ok();
    }

    /**
     * The shared delivery engine for {@code /msg} and {@code /reply}: gate, then deliver. The target is
     * assumed resolved (online and not hidden) by the caller's lookup; the remaining gates are mute, self,
     * toggle, and ignore.
     */
    Result<Unit, MessagingError> deliver(PlayerRef sender, PlayerRef target, MessageBody body) {
        Result<Unit, MessagingError> gate = gate(sender, target);
        if (gate.isErr()) {
            return reject(sender, gate.errorOrThrow());
        }
        if (ignores.load(target).blocks(sender, IgnoreChannel.MESSAGE)) {
            return silentlyDrop(sender, target, body);
        }
        dispatch(sender, target, body);
        return Result.ok();
    }

    private Result<Unit, MessagingError> gate(PlayerRef sender, PlayerRef target) {
        if (mute.isMuted(sender)) {
            return Result.err(MessagingError.SENDER_MUTED);
        }
        if (sender.equals(target)) {
            return Result.err(MessagingError.SELF);
        }
        if (!toggles.acceptsMessages(target)) {
            return Result.err(MessagingError.TARGET_TOGGLED_OFF);
        }
        return Result.ok();
    }

    private void dispatch(PlayerRef sender, PlayerRef target, MessageBody body) {
        delivery.echoSent(sender, target, body);
        delivery.deliverMessage(sender, target, body);
        notifyIfAfk(sender, target);
        fanOutSpies(sender, target, body);
        rememberBothSides(sender, target);
        events.publish(new PrivateMessageSent(sender, target, body, clock.instant()));
    }

    private void notifyIfAfk(PlayerRef sender, PlayerRef target) {
        // Fired only on a real delivery, never on a silently-dropped ignore: an AFK notice there would reveal
        // that the ignoring target is online, breaking the ignore-is-not-observable contract.
        Optional<String> reason = afk.afkReasonOf(target);
        if (reason.isPresent()) {
            notifier.send(
                    sender,
                    MessagingMessageKey.MSG_TARGET_AFK,
                    Map.of("player", target.name(), "reason", reason.get()));
        }
    }

    private void fanOutSpies(PlayerRef sender, PlayerRef target, MessageBody body) {
        // Every global ALL spy plus every targeted spy watching either party. A targeted spy never sees an
        // unrelated conversation; a global spy sees them all, unchanged. A spy who is a party is skipped.
        for (PlayerRef observer : socialSpy.observersOf(sender, target)) {
            if (!observer.equals(sender) && !observer.equals(target)) {
                delivery.deliverSpy(observer, sender, target, body);
            }
        }
    }

    private void rememberBothSides(PlayerRef sender, PlayerRef target) {
        java.time.Instant now = clock.instant();
        conversations.remember(sender, LastConversation.with(target, now));
        conversations.remember(target, LastConversation.with(sender, now));
    }

    private Result<Unit, MessagingError> silentlyDrop(PlayerRef sender, PlayerRef target, MessageBody body) {
        // The recipient ignores the sender: do not deliver, but echo as if delivered so the ignore is not
        // observable. Spies still see the attempt; the reply target is still captured for the sender.
        delivery.echoSent(sender, target, body);
        fanOutSpies(sender, target, body);
        conversations.remember(sender, LastConversation.with(target, clock.instant()));
        return Result.ok();
    }

    private Result<Unit, MessagingError> reject(PlayerRef sender, MessagingError error) {
        notifier.send(sender, error.messageKey());
        return Result.err(error);
    }
}
