package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
 * {@code /staffrollback <staff> [limit]}: revoke a (rogue or mistaken) staff member's still-active sanctions
 * un-ban, un-mute and clear-warns the targets they sanctioned that remain under that sanction now. The
 * {@code StaffRollback} use case reads the staff member's recent history (newest-first, capped at {@code limit}),
 * gates every revoke on a live-state read so a lifted, lapsed or re-applied sanction is left alone, and reports a
 * summary of what it undid. Gated by its own {@code uxmessentials.moderation.staffrollback} node, separate from
 * the act-on-sanction nodes so the power to mass-revoke is granted deliberately.
 *
 * <p>The staff member resolves online-first, then from the profile cache, so a since-logged-off (or rogue,
 * already-removed) account is still reachable. The {@code limit} defaults to {@link #DEFAULT_LIMIT} and is
 * clamped to {@link #MAX_LIMIT} so the bounded history scan stays within budget; the whole rollback is hopped off
 * the tick thread through the {@link Scheduler} port since it issues a string of DB writes.
 */
@NullMarked
public final class StaffRollbackCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.staffrollback";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final Scheduler scheduler;

    public StaffRollbackCommand(ModerationServices services, Messages messages, MessageSink sink, Scheduler scheduler) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("staffrollback")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("staff")
                        .executes(ctx -> run(ctx, DEFAULT_LIMIT))
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1))
                                .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "limit")))))
                .build();
    }

    @Override
    public String description() {
        return "Revoke a staff member's still-active sanctions.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, int requested) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> staff = targetByName(ctx, ctx.getArgument("staff", String.class));
        int limit = Math.min(requested, MAX_LIMIT);
        staff.ifPresent(member -> scheduler.async(() -> services.staffRollback().rollback(actor, member, limit)));
        return Command.SINGLE_SUCCESS;
    }
}
