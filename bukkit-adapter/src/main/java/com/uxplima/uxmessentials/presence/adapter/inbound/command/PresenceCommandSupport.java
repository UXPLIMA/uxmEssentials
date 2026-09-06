package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every presence Brigadier command holds: the constructed {@link PresenceServices} and the
 * {@link Messages} catalog (the latter only for the players-only rejection. All success feedback flows through
 * the use cases' {@code MessageSink}). Concrete command classes extend this so each stays focused on building
 * its node and mapping its arguments to one use-case call. Both presence commands act on the sender only
 * there is no {@code [player]} target form, so this base needs no others-gate.
 */
@NullMarked
abstract class PresenceCommandSupport {

    private static final MessageKey PLAYERS_ONLY = SharedMessageKey.COMMAND_PLAYERS_ONLY;

    final PresenceServices services;
    final CommandFeedback feedback;
    final Scheduler scheduler;

    PresenceCommandSupport(PresenceServices services, Messages messages, Scheduler scheduler) {
        this.services = Objects.requireNonNull(services, "services");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Run {@code reply} on the thread that owns {@code sender}: a player sender's own entity region thread (where
     * {@code sendMessage} is valid under Folia), or inline for the console, which has no region. The roster
     * commands ({@code /list}, {@code /staff}, {@code /realname}, {@code /whois}) enumerate the online set on the
     * global region thread and then route their one reply back through here.
     */
    final void replyOnSenderThread(CommandSender sender, Runnable reply) {
        if (sender instanceof Player player) {
            scheduler.onEntity(BukkitRefs.toRef(player), reply);
        } else {
            reply.run();
        }
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, PLAYERS_ONLY, Map.of());
        return null;
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }
}
