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
 * {@code /staffhistory <staff>}: review the sanctions a staff member has issued, newest-first, the mirror of
 * {@code /history}, scoped by who did the punishing rather than who was punished, so an operator can audit a
 * staff member's disciplinary activity. The {@code ReviewStaffHistory} use case runs the bounded, append-only
 * history query (indexed by actor) and resolves each row's target name. It is gated by its own
 * {@code uxmessentials.moderation.staffhistory} node. The lookup is hopped off the tick thread through the
 * {@link Scheduler} port; the staff member resolves online-first, then from the profile cache, so an offline
 * account is still reviewable.
 */
@NullMarked
public final class StaffHistoryCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.staffhistory";

    private final Scheduler scheduler;

    public StaffHistoryCommand(ModerationServices services, Messages messages, MessageSink sink, Scheduler scheduler) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("staffhistory")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("staff").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Review the sanctions a staff member issued.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> staff = targetByName(ctx, ctx.getArgument("staff", String.class));
        staff.ifPresent(
                member -> scheduler.async(() -> services.reviewStaffHistory().show(actor, member)));
        return Command.SINGLE_SUCCESS;
    }
}
