package com.uxplima.uxmessentials.vote.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.adapter.VoteServices;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /voteparty} ({@code uxmessentials.voteparty.use}): show the invoking player the progress towards the
 * next vote party: the accumulated count, the configured threshold, and how many votes remain. The count is
 * read from the durable repository through {@link com.uxplima.uxmessentials.vote.application.VotePartyStatus},
 * so the read runs off the tick thread via the {@link Scheduler} port's async seam; the reply hops back to the
 * viewer through the message sink inside the use case.
 *
 * <p>Admin subcommands (console-capable) are nested under the same literal:
 *
 * <ul>
 *   <li>{@code force} ({@code uxmessentials.voteparty.admin}, default op), fire the party immediately via
 *       {@link com.uxplima.uxmessentials.vote.application.ForceParty}, regardless of the current counter.
 *   <li>{@code set <n>}, set the party counter to an exact value via
 *       {@link com.uxplima.uxmessentials.vote.application.SetPartyCount}.
 *   <li>{@code add <n>}. Add to the party counter (fires a party if the threshold is now reached) via
 *       {@link com.uxplima.uxmessentials.vote.application.AddPartyCount}.
 * </ul>
 *
 * <p>The no-arg status display is player-only; the admin subcommands accept a console sender.
 */
@NullMarked
public final class VotePartyCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.voteparty.use";
    private static final String ADMIN_PERMISSION = "uxmessentials.voteparty.admin";

    private final VoteServices services;
    private final CommandFeedback feedback;

    public VotePartyCommand(VoteServices services) {
        this.services = Objects.requireNonNull(services, "services");
        this.feedback = new CommandFeedback(services.messages());
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("voteparty")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::status)
                .then(Commands.literal("force")
                        .requires(src -> src.getSender().hasPermission(ADMIN_PERMISSION))
                        .executes(this::forceParty))
                .then(Commands.literal("set")
                        .requires(src -> src.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                .executes(this::setCount)))
                .then(Commands.literal("add")
                        .requires(src -> src.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(this::addCount)))
                .build();
    }

    @Override
    public String description() {
        return "Show progress towards the next vote party.";
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        services.scheduler().async(() -> services.votePartyStatus().show(who));
        return Command.SINGLE_SUCCESS;
    }

    private int forceParty(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = CommandFeedback.refOf(sender);
        services.scheduler().async(() -> services.forceParty().execute(actor));
        return Command.SINGLE_SUCCESS;
    }

    private int setCount(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        int count = ctx.getArgument("count", Integer.class);
        PlayerRef actor = CommandFeedback.refOf(sender);
        services.scheduler().async(() -> services.setPartyCount().set(actor, count));
        return Command.SINGLE_SUCCESS;
    }

    private int addCount(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        int amount = ctx.getArgument("amount", Integer.class);
        PlayerRef actor = CommandFeedback.refOf(sender);
        services.scheduler().async(() -> services.addPartyCount().add(actor, amount));
        return Command.SINGLE_SUCCESS;
    }

    private @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, VoteMessageKey.VOTE_PLAYERS_ONLY, Map.of());
        return null;
    }
}
