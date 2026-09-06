package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /reply <text>}: answer the last conversation without naming the partner. The reply target resolution
 * and the reply-TTL rule are the {@link com.uxplima.uxmessentials.messaging.application.Reply} use case's
 * job: a stale or now-unreachable (offline / vanished) partner declines with the no-reply-target message.
 * This handler only maps the greedy text.
 */
@NullMarked
public final class ReplyCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.reply";

    public ReplyCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("reply")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Reply to your last private conversation.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        MessageBody text = body(sender, ctx.getArgument("text", String.class));
        if (text == null) {
            return 0;
        }
        services.reply().reply(ref(sender), text);
        return Command.SINGLE_SUCCESS;
    }
}
