package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Optional;
import java.util.function.BiConsumer;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A {@code /<verb> <player>} command whose only argument is a target: {@code /unmute}, {@code /unjail},
 * {@code /freeze}, {@code /unfreeze}, {@code /seen}, {@code /seenip}, {@code /warns}. The shared shape, a
 * single word target, the permission gate, the online-or-offline resolution, lives here so each concrete
 * verb is one constructor passing its literal, node, description and the {@code (actor, target)} action.
 */
@NullMarked
public final class SimpleTargetCommand extends ModerationCommandSupport implements CommandRegistration {

    private final String literal;
    private final String permission;
    private final String description;
    private final BiConsumer<PlayerRef, PlayerRef> action;

    public SimpleTargetCommand(
            String literal,
            String permission,
            String description,
            BiConsumer<PlayerRef, PlayerRef> action,
            ModerationServices services,
            Messages messages,
            MessageSink sink) {
        super(services, messages, sink);
        this.literal = literal;
        this.permission = permission;
        this.description = description;
        this.action = action;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return description;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> action.accept(actor, to));
        return Command.SINGLE_SUCCESS;
    }
}
