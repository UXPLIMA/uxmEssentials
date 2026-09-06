package com.uxplima.uxmessentials.security.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared plumbing for the two security commands: resolving the invoking player, sending a synchronous in-line
 * rejection on the tick thread, and delivering a message from the async worker thread through the sink. The split
 * mirrors the threading contract. Cheap gating replies go out with {@link CommandFeedback} on the calling thread,
 * while the outcome of the off-thread crypto/DB work is resolved with {@link Messages} (a pure function) and handed
 * to the {@link MessageSink}, which re-enters the entity thread to deliver it.
 */
@NullMarked
abstract class SecurityCommandSupport {

    protected final Scheduler scheduler;
    protected final Messages messages;
    private final MessageSink sink;
    private final CommandFeedback feedback;

    protected SecurityCommandSupport(Scheduler scheduler, Messages messages, MessageSink sink) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.feedback = new CommandFeedback(messages);
    }

    /** The invoking player, or {@code null} after sending the players-only line to a non-player sender. */
    protected @Nullable Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
        return null;
    }

    /** The domain ref for a live player. */
    protected PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /** Send {@code key} to {@code sender} synchronously on the calling (tick) thread: for the cheap gating replies. */
    protected void reply(CommandSender sender, MessageKey key) {
        feedback.send(sender, key);
    }

    /** Resolve {@code key} for {@code viewer} and deliver it through the sink, safe to call off the tick thread. */
    protected void notify(PlayerRef viewer, MessageKey key) {
        notify(viewer, key, Map.of());
    }

    /** Resolve {@code key} with {@code placeholders} for {@code viewer} and deliver it through the sink. */
    protected void notify(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        sink.deliver(viewer, messages.resolve(viewer, key, placeholders));
    }

    /** Deliver {@code key} to whoever ran the command, from off the tick thread. */
    protected void notifySender(CommandSender sender, MessageKey key) {
        notifySender(sender, key, Map.of());
    }

    /**
     * Deliver {@code key} to whoever ran the command, from off the tick thread. A player goes through the sink, which
     * hops to their entity thread. The console cannot: the sink resolves its recipient by looking the viewer's uuid up
     * among the online players, and a console sender is not one, so its reply would be rendered and then silently
     * dropped. That branch renders in the sender's locale and writes on a global-scheduler hop instead, which is what
     * keeps the staff reads answering an operator who runs them from the server console rather than in game.
     */
    protected void notifySender(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        if (sender instanceof Player player) {
            notify(ref(player), key, placeholders);
            return;
        }
        PlayerRef viewer = CommandFeedback.refOf(sender);
        String rendered = messages.resolve(viewer, key, placeholders);
        scheduler.onGlobal(() -> feedback.sendRendered(sender, viewer, rendered));
    }
}
