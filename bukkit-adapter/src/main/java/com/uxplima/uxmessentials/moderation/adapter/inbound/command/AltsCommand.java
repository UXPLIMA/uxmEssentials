package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

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
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /alts <player>}: list the accounts that share the target's last-known IP (alt detection). A read-only
 * companion of {@code /seenip}. The {@code ListAlts} use case runs the bounded seen/altsByIp lookup and
 * renders the alt set as a list. It shares the {@code uxmessentials.moderation.seen} node with {@code /seen}
 * and {@code /seenip}, the same last-seen/last-IP read surface. The lookup is hopped off the tick thread
 * through the {@link Scheduler} port so a large seen table never blocks the command.
 */
@NullMarked
public final class AltsCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.seen";

    private final Scheduler scheduler;

    public AltsCommand(ModerationServices services, Messages messages, MessageSink sink, Scheduler scheduler) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("alts")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "List accounts sharing a player's IP.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> scheduler.async(() -> services.listAlts().list(actor, to)));
        return Command.SINGLE_SUCCESS;
    }
}
