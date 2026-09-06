package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /socialspy}: flip whether a staff member observes other players' private messages (audit-relevant),
 * in one of two forms. With no argument it toggles the global ALL flag, observe every private message,
 * while {@code /socialspy <player>} toggles one player on the staff member's target set, so they observe only
 * conversations that player is a party to. The target name completes against the online roster but parses any
 * name, so an offline player is targetable too (the watch is a UUID, resolved later by the send-path
 * fan-out). The flip and the feedback are the
 * {@link com.uxplima.uxmessentials.messaging.application.SocialSpy} use case's job; this handler maps the
 * invoking staff member and the optional target. Both forms are gated by the one socialspy node.
 */
@NullMarked
public final class SocialSpyCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.socialspy";

    public SocialSpyCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("socialspy")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggleGlobal)
                .then(CommandSuggestions.playerArgument("player").executes(this::watchTarget))
                .build();
    }

    @Override
    public String description() {
        return "Observe other players' private messages, globally or for one player.";
    }

    private int toggleGlobal(CommandContext<CommandSourceStack> ctx) {
        Player staff = player(ctx);
        if (staff != null) {
            services.socialSpy().toggle(ref(staff));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int watchTarget(CommandContext<CommandSourceStack> ctx) {
        Player staff = player(ctx);
        if (staff == null) {
            return 0;
        }
        PlayerRef spy = ref(staff);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findByName(name);
        if (target.isEmpty()) {
            notify(spy, UNKNOWN_PLAYER, Map.of("player", name));
            return 0;
        }
        services.socialSpy().watch(spy, target.get());
        return Command.SINGLE_SUCCESS;
    }
}
