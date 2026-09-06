package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
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
 * {@code /togglejail <player> [jail] [reason]}: release the target when they are jailed, otherwise jail them in
 * the named jail (or the first configured jail when the name is omitted). A convenience wrapper for staff who
 * do not want to check first whether a player is jailed: the direction is decided by the current jail row.
 * The exempt / unknown-jail gating, the audit lines and the teleport are the {@code ToggleJail} use case's
 * job (which reuses {@code Jail}/{@code Unjail}); this handler only maps the name, the optional jail and the
 * greedy reason. The target may be offline.
 */
@NullMarked
public final class ToggleJailCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.togglejail";

    public ToggleJailCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("togglejail")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> run(ctx, "", Optional.empty()))
                        .then(Commands.argument("jail", StringArgumentType.word())
                                .suggests(CommandSuggestions.fromStrings(
                                        () -> services.listJails().suggestionNames()))
                                .executes(ctx -> run(ctx, ctx.getArgument("jail", String.class), Optional.empty()))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx ->
                                                run(ctx, ctx.getArgument("jail", String.class), optionalReason(ctx))))))
                .build();
    }

    @Override
    public String description() {
        return "Toggle a player's jail: release if jailed, otherwise jail them.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, String jail, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.toggleJail().toggle(actor, to, jail, reason));
        return Command.SINGLE_SUCCESS;
    }
}
