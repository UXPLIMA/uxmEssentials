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
 * {@code /ignorelist}: open the ignore-list manager GUI, the same view {@code /ignore} with no argument opens,
 * so the list lives in one place and the standalone command stays a familiar entry point into it. It takes no
 * argument and reuses the {@code uxmessentials.msg.ignore} node, since seeing your own list is part of managing
 * it; the rendered list (the heads to review and un-ignore, the add control) is the GUI's job.
 */
@NullMarked
public final class IgnoreListCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.ignore";

    private final MessagingGuiViews views;

    public IgnoreListCommand(MessagingServices services, Messages messages, MessageSink sink, MessagingGuiViews views) {
        super(services, messages, sink);
        this.views = Objects.requireNonNull(views, "views");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ignorelist")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Open your ignore list.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        views.openIgnore(sender, ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
