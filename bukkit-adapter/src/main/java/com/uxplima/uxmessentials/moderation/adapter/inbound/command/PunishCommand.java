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
 * {@code /punish <player> <template>}: apply a configured punishment template, a preset reason plus an
 * optional duration, to a target. The template resolution and the ban/tempban dispatch (with the exempt
 * gate, kick, audit, history, duration cap and broadcast) are the {@code Punish} use case's job; this handler
 * only maps the name and the template argument and suggests the configured template names. An unknown template
 * is reported by the use case. Whether the sanction broadcasts follows the module's silent-by-default setting.
 */
@NullMarked
public final class PunishCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.templates";

    private final boolean silentByDefault;

    public PunishCommand(ModerationServices services, Messages messages, MessageSink sink, boolean silentByDefault) {
        super(services, messages, sink);
        this.silentByDefault = silentByDefault;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("punish")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests(CommandSuggestions.fromStrings(
                                        () -> services.punish().templateNames()))
                                .executes(this::run)))
                .build();
    }

    @Override
    public String description() {
        return "Apply a configured punishment template to a player.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        String template = ctx.getArgument("template", String.class);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.punish().punish(actor, to, template, silentByDefault));
        return Command.SINGLE_SUCCESS;
    }
}
