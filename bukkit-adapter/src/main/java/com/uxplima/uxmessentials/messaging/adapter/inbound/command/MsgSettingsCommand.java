package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MessagingGuiViews;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /msgsettings}: open the per-player messaging settings panel. The accept-messages ({@code /msgtoggle})
 * toggle every player flips for themselves, plus the social-spy ({@code /socialspy}) toggle a staff member sees.
 * A players-only, self-service command gated by {@code uxmessentials.msgsettings.use}; it opens the same
 * {@link MessagingGuiViews} settings panel the {@code /uxmess gui} hub does. It mutates nothing itself, every
 * flip in the panel routes through the existing message-toggle / social-spy stores the toggle commands use.
 */
@NullMarked
public final class MsgSettingsCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msgsettings.use";

    private final MessagingGuiViews views;

    public MsgSettingsCommand(
            MessagingServices services, Messages messages, MessageSink sink, MessagingGuiViews views) {
        super(services, messages, sink);
        this.views = Objects.requireNonNull(views, "views");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("msgsettings")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "Open your personal messaging settings panel.";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        views.openSettings(sender, ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
