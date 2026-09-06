package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MessagingGuiViews;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ignore [player]}: with a player argument, add them to the owner's persistent ignore list, the verb
 * form of the ignore behaviour. With no argument it opens the ignore-list manager GUI, where the owner reviews
 * and un-ignores the players they ignore and adds new ones. The target is resolved offline-capably by name
 * (the ignore store is UUID-keyed, so a player who has joined before can be ignored while offline); only a name
 * the server has never seen is rejected as unknown. The self-check, the idempotent store write, and the
 * feedback are the {@link com.uxplima.uxmessentials.messaging.application.Ignore} use case's job.
 */
@NullMarked
public final class IgnoreCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.ignore";

    private final MessagingGuiViews views;

    public IgnoreCommand(MessagingServices services, Messages messages, MessageSink sink, MessagingGuiViews views) {
        super(services, messages, sink);
        this.views = Objects.requireNonNull(views, "views");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ignore")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::openGui)
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Ignore a player's messages, or open your ignore list.";
    }

    private int openGui(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        views.openIgnore(sender, ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef owner = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findByName(name);
        if (target.isEmpty()) {
            notify(owner, UNKNOWN_PLAYER, Map.of("player", name));
            return 0;
        }
        services.ignore().ignore(owner, target.get());
        return Command.SINGLE_SUCCESS;
    }
}
