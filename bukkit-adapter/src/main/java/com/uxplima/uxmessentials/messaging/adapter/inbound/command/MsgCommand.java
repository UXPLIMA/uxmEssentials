package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

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
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /msg <player> <text>}: send a private message. The target is a string-name argument that completes
 * against the online roster but parses any name, so an <em>offline</em> player is reachable too: an online
 * target takes the live delivery path (mute, self, toggle, ignore, the socialspy fan-out, the both-sides
 * reply capture. The {@link com.uxplima.uxmessentials.messaging.application.SendMessage} use case's job, plus
 * the AFK courtesy notice), while a genuinely-offline target whose profile resolves takes the 4-arg
 * {@code send(..., targetOnline=false)} so the use case's offline → mail fallback runs (config-gated by
 * {@code offline-to-mail}).
 *
 * <p>The vanish privacy seam is preserved exactly, and is made <em>indistinguishable</em> from a genuine
 * offline: an online target the sender cannot see (the {@code vanish} gate reports hidden) is never delivered
 * live, but rather than short-circuiting with a hidden-only response it is routed through the same 4-arg
 * {@code send(..., targetOnline=false)} as a genuinely-offline player. So with {@code offline-to-mail} on the
 * sender gets {@code MSG_SENT_TO_MAIL} and the note waits in the vanished player's box; with it off the sender
 * gets {@code MSG_TARGET_OFFLINE}, byte-identical to a real offline target in both config modes, so the
 * sender can never infer "online-but-hidden" from the feedback. A hidden player's presence is never leaked and
 * they never receive a live delivery. Only a name that resolves to no online player and no played-before
 * profile is rejected as unknown.
 */
@NullMarked
public final class MsgCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.msg.use";

    public MsgCommand(MessagingServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("msg")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(this::run)))
                .build();
    }

    @Override
    public String description() {
        return "Send a private message to a player.";
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
        PlayerRef from = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> online = services.players().findOnlineByName(name);
        if (online.isPresent()) {
            PlayerRef target = online.get();
            if (services.vanish().isHiddenFrom(from, target)) {
                // The target is online but the sender cannot see them. Route through the offline path so the
                // outcome is identical to a genuinely-offline target (mail when offline-to-mail is on, the
                // TARGET_OFFLINE rejection when it is off), never a live delivery, never a hidden-only tell.
                services.sendMessage().send(from, target, text, false);
                return Command.SINGLE_SUCCESS;
            }
            // The online path: a visible target takes the live delivery through the 3-arg send (targetOnline=true).
            services.sendMessage().send(from, target, text);
            return Command.SINGLE_SUCCESS;
        }
        Optional<PlayerRef> offline = services.players().findByName(name);
        if (offline.isEmpty()) {
            notify(from, UNKNOWN_PLAYER, Map.of("player", name));
            return 0;
        }
        // The offline path: a played-before profile that is not online. The 4-arg send with targetOnline=false
        // hands the use case the offline → mail fallback (gated by the offline-to-mail policy in core).
        services.sendMessage().send(from, offline.get(), text, false);
        return Command.SINGLE_SUCCESS;
    }
}
