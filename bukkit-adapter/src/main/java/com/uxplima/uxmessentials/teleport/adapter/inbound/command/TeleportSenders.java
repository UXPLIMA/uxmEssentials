package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Boundary helpers shared by the teleport Brigadier commands: narrow a command source to a {@link Player}
 * (rejecting console with the players-only message) and render an operator-facing failure component for a
 * source that has no per-player locale. Player-facing feedback flows through the {@code MessageSink}; only
 * the players-only / unknown-player rejection, which a console may see, is rendered here, still through
 * the {@link Messages} catalog so no inline literal exists.
 */
@NullMarked
final class TeleportSenders {

    /** The catalog key for a console invoking a player-only command. */
    static final MessageKey PLAYERS_ONLY = SharedMessageKey.COMMAND_PLAYERS_ONLY;

    private TeleportSenders() {}

    /** The invoking player, or {@code null} (with a players-only reply sent) when the source is console. */
    static @Nullable Player requirePlayer(CommandContext<CommandSourceStack> ctx, Messages messages) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        new CommandFeedback(messages).send(sender, PLAYERS_ONLY, java.util.Map.of());
        return null;
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef refOf(Player player) {
        return BukkitRefs.toRef(player);
    }
}
