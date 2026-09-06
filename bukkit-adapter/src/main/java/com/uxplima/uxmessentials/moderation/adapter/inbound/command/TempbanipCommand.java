package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.BanIp;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tempbanip <player|ip> <duration> [reason]}: ban an address until a wall-clock expiry. The target is
 * resolved exactly as {@code /banip} does. An IP literal is banned directly, a player name resolves their
 * last-seen IP from the DB-backed seen record, and the {@code TempBanIp} use case enforces that the duration
 * is timed (a permanent IP ban is {@code /banip}). Shares the {@code moderation.banip} node, like
 * {@code /unbanip} does.
 */
@NullMarked
public final class TempbanipCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.banip";
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9.:a-fA-F]+$");

    public TempbanipCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tempbanip")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("target")
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .executes(ctx -> run(ctx, Optional.empty()))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, optionalReason(ctx))))))
                .build();
    }

    @Override
    public String description() {
        return "Ban an IP for a duration.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        String argument = ctx.getArgument("target", String.class);
        String duration = ctx.getArgument("duration", String.class);
        Optional<BanIp.Target> resolved = resolveTarget(ctx, argument);
        resolved.ifPresent(target -> services.tempBanIp().tempBanIp(actor, target, duration, reason));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<BanIp.Target> resolveTarget(CommandContext<CommandSourceStack> ctx, String argument) {
        if (argument.contains(".") || argument.contains(":")) {
            return IP_LITERAL.matcher(argument).matches()
                    ? Optional.of(new BanIp.Target(argument, Optional.empty()))
                    : Optional.empty();
        }
        return targetByName(ctx, argument).flatMap(player -> byPlayer(ctx, player));
    }

    private Optional<BanIp.Target> byPlayer(CommandContext<CommandSourceStack> ctx, PlayerRef player) {
        Optional<String> ip = services.repository().seen(player).flatMap(SeenRecord::lastIp);
        if (ip.isEmpty()) {
            notify(ctx, ModerationMessageKey.SEENIP_NO_IP, Map.of("player", player.name()));
            return Optional.empty();
        }
        return Optional.of(new BanIp.Target(ip.get(), Optional.of(player.uuid())));
    }
}
