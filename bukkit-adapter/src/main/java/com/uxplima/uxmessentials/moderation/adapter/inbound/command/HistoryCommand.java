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
 * {@code /history <player>}: review a player's full disciplinary record newest-first, every ban, mute, warn
 * and kick folded into one timeline, where {@code /banhistory} and {@code /mutehistory} each stay scoped to one
 * family. The {@code ReviewSanctionHistory} use case runs the bounded, append-only history query. It is gated by
 * its own {@code uxmessentials.moderation.history} node so the unified record can be granted independently of
 * the act-on-sanction nodes. The lookup is hopped off the tick thread through the {@link Scheduler} port so a
 * large history table never blocks the command. The target resolves online-first, then from the profile cache,
 * so an offline player's record is still reviewable.
 */
@NullMarked
public final class HistoryCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.history";

    private final Scheduler scheduler;

    public HistoryCommand(ModerationServices services, Messages messages, MessageSink sink, Scheduler scheduler) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("history")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Review a player's full sanction history.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(
                to -> scheduler.async(() -> services.reviewSanctionHistory().show(actor, to)));
        return Command.SINGLE_SUCCESS;
    }
}
