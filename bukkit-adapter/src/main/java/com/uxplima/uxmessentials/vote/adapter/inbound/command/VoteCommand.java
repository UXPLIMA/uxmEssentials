package com.uxplima.uxmessentials.vote.adapter.inbound.command;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

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
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.adapter.VoteServices;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.domain.Vote;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /vote} ({@code uxmessentials.vote.use}): show the server's configured vote links to the invoking
 * player. Subcommands:
 *
 * <ul>
 *   <li>{@code sites}: open the vote-sites GUI (or fall back to chat links when gui mode is off).
 *   <li>{@code claim}. Pay out the rewards the player accrued while offline (the queue drain that the
 *       join handler runs automatically when auto-claim is on). Gated by {@code uxmessentials.vote.use}.
 *   <li>{@code total [player]}. Show the sender's (or another player's) accumulated vote totals
 *       across all periods. Gated by {@code uxmessentials.vote.use}.
 *   <li>{@code streak [player]}. Show the sender's (or another player's) current and best
 *       consecutive-day voting streak. Gated by {@code uxmessentials.vote.use}.
 *   <li>{@code top [daily|weekly|monthly|alltime]}, show the leaderboard for the given period
 *       (default {@code monthly}). Gated by {@code uxmessentials.vote.top}.
 *   <li>{@code next}: show when the player can next vote on each configured site.
 *   <li>{@code last}: show when the player last voted on each configured site.
 *   <li>{@code remind}: toggle vote reminder messages on/off for the sender.
 *   <li>{@code broadcasts}: toggle whether the sender sees server-wide vote broadcasts.
 *   <li>{@code admin givevote <player> [amount]}. Inject synthetic votes for a player (offline-capable),
 *       gated by {@code uxmessentials.vote.admin}.
 *   <li>{@code admin reset <player>}. Reset all vote totals for a player (offline-capable),
 *       gated by {@code uxmessentials.vote.admin}.
 * </ul>
 *
 * <p>A console source gets the players-only rejection for the base command; admin subcommands accept the
 * console. The {@code total}, {@code top}, {@code next}, {@code last}, and admin reads run off the tick
 * thread so repository I/O stays async.
 */
@NullMarked
public final class VoteCommand implements CommandRegistration {

    private static final String USE_PERMISSION = "uxmessentials.vote.use";
    private static final String TEST_PERMISSION = "uxmessentials.vote.testreward";
    private static final String TOP_PERMISSION = "uxmessentials.vote.top";
    private static final String ADMIN_PERMISSION = "uxmessentials.vote.admin";

    private final VoteServices services;
    private final CommandFeedback feedback;
    private final Supplier<ListDisplayMode> displayMode;

    public VoteCommand(VoteServices services, Supplier<ListDisplayMode> displayMode) {
        this.services = Objects.requireNonNull(services, "services");
        this.feedback = new CommandFeedback(services.messages());
        this.displayMode = Objects.requireNonNull(displayMode, "displayMode");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vote")
                .requires(src -> src.getSender().hasPermission(USE_PERMISSION))
                .executes(this::showLinks)
                .then(Commands.literal("sites").executes(this::showSites))
                .then(Commands.literal("testreward")
                        .requires(src -> src.getSender().hasPermission(TEST_PERMISSION))
                        .executes(this::testReward))
                .then(Commands.literal("claim").executes(this::claim))
                .then(Commands.literal("total")
                        .executes(this::totalSelf)
                        .then(CommandSuggestions.playerArgument("player").executes(this::totalOther)))
                .then(Commands.literal("streak")
                        .executes(this::streakSelf)
                        .then(CommandSuggestions.playerArgument("player").executes(this::streakOther)))
                .then(Commands.literal("top")
                        .requires(src -> src.getSender().hasPermission(TOP_PERMISSION))
                        .executes(this::topMonthly)
                        .then(Commands.literal("daily").executes(ctx -> topPeriod(ctx, VotePeriod.DAILY)))
                        .then(Commands.literal("weekly").executes(ctx -> topPeriod(ctx, VotePeriod.WEEKLY)))
                        .then(Commands.literal("monthly").executes(ctx -> topPeriod(ctx, VotePeriod.MONTHLY)))
                        .then(Commands.literal("alltime").executes(ctx -> topPeriod(ctx, VotePeriod.ALLTIME))))
                .then(Commands.literal("next").executes(this::showNext))
                .then(Commands.literal("last").executes(this::showLast))
                .then(Commands.literal("remind").executes(this::toggleRemind))
                .then(Commands.literal("broadcasts").executes(this::toggleBroadcasts))
                .then(Commands.literal("admin")
                        .requires(src -> src.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(Commands.literal("givevote")
                                .then(CommandSuggestions.playerArgument("player")
                                        .executes(this::giveVoteDefault)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(this::giveVote))))
                        .then(Commands.literal("reset")
                                .then(CommandSuggestions.playerArgument("player")
                                        .executes(this::resetTotals))))
                .build();
    }

    @Override
    public String description() {
        return "Show the server's vote links.";
    }

    private int showLinks(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        // In GUI mode, open the sites GUI when the catalog is non-empty.
        if (displayMode.get() == ListDisplayMode.GUI
                && services.voteSitesGui().isEnabled()
                && !services.voteSitesGui().catalog().sites().isEmpty()) {
            services.voteSitesGui().open(sender);
            return Command.SINGLE_SUCCESS;
        }
        services.voteLinks().show(BukkitRefs.toRef(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int showSites(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        // If the catalog is empty or GUI is disabled, fall back to chat links.
        if (!services.voteSitesGui().isEnabled()
                || services.voteSitesGui().catalog().sites().isEmpty()) {
            services.voteLinks().show(BukkitRefs.toRef(sender));
            return Command.SINGLE_SUCCESS;
        }
        services.voteSitesGui().open(sender);
        return Command.SINGLE_SUCCESS;
    }

    private int testReward(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        services.scheduler().async(() -> services.handleVote().handle(new Vote(who, "test", Instant.now())));
        feedback.send(sender, VoteMessageKey.VOTE_TESTREWARD, Map.of());
        return Command.SINGLE_SUCCESS;
    }

    private int claim(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        // The drain is a transactional DB read-and-delete, so it hops off the tick thread; the count is only
        // known after it runs, so the confirmation is sent from the same async body once the batch is paid.
        services.scheduler().async(() -> {
            int paid = services.applyQueuedRewards().applyFor(who);
            feedback.send(
                    sender,
                    paid > 0 ? VoteMessageKey.VOTE_CLAIM_PAID : VoteMessageKey.VOTE_CLAIM_EMPTY,
                    paid > 0 ? Map.of("count", Integer.toString(paid)) : Map.of());
        });
        return Command.SINGLE_SUCCESS;
    }

    private int totalSelf(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        services.scheduler().async(() -> services.showVoteTotals().show(who, who));
        return Command.SINGLE_SUCCESS;
    }

    private int totalOther(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.playerLookup().findByName(name);
        if (target.isEmpty()) {
            feedback.send(sender, VoteMessageKey.VOTE_TOTAL_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = target.get();
        services.scheduler().async(() -> services.showVoteTotals().show(viewer, resolved));
        return Command.SINGLE_SUCCESS;
    }

    private int streakSelf(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        services.scheduler().async(() -> services.showVoteStreak().show(who, who));
        return Command.SINGLE_SUCCESS;
    }

    private int streakOther(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.playerLookup().findByName(name);
        if (target.isEmpty()) {
            feedback.send(sender, VoteMessageKey.VOTE_TOTAL_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = target.get();
        services.scheduler().async(() -> services.showVoteStreak().show(viewer, resolved));
        return Command.SINGLE_SUCCESS;
    }

    private int topMonthly(CommandContext<CommandSourceStack> ctx) {
        return topPeriod(ctx, VotePeriod.MONTHLY);
    }

    private int topPeriod(CommandContext<CommandSourceStack> ctx, VotePeriod period) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(sender);
        // Resolve real names from the PlayerLookup; fall back to UUID string when profile is unknown.
        services.scheduler()
                .async(() -> services.topVoters()
                        .top(
                                viewer,
                                period,
                                uuid -> services.playerLookup()
                                        .findByUuid(uuid)
                                        .map(PlayerRef::name)
                                        .orElse(uuid.toString().toLowerCase(Locale.ROOT))));
        return Command.SINGLE_SUCCESS;
    }

    private int showNext(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        Instant now = Instant.now();
        services.scheduler().async(() -> services.showNextVote().show(who, now));
        return Command.SINGLE_SUCCESS;
    }

    private int showLast(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        Instant now = Instant.now();
        services.scheduler().async(() -> services.showLastVote().show(who, now));
        return Command.SINGLE_SUCCESS;
    }

    private int toggleRemind(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        // PDC is region-bound; the command fires on the player's region thread, so the toggle is in-line.
        boolean nowWants = services.reminderPreferences().toggle(who);
        feedback.send(
                sender, nowWants ? VoteMessageKey.VOTE_REMIND_ENABLED : VoteMessageKey.VOTE_REMIND_DISABLED, Map.of());
        return Command.SINGLE_SUCCESS;
    }

    private int toggleBroadcasts(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        // PDC is region-bound; the command fires on the player's region thread, so the toggle is in-line.
        boolean nowReceives = services.broadcastVisibility().toggle(who);
        feedback.send(
                sender,
                nowReceives ? VoteMessageKey.VOTE_BROADCASTS_SHOWN : VoteMessageKey.VOTE_BROADCASTS_HIDDEN,
                Map.of());
        return Command.SINGLE_SUCCESS;
    }

    private int giveVoteDefault(CommandContext<CommandSourceStack> ctx) {
        return giveVoteImpl(ctx, 1);
    }

    private int giveVote(CommandContext<CommandSourceStack> ctx) {
        int amount = ctx.getArgument("amount", Integer.class);
        return giveVoteImpl(ctx, amount);
    }

    private int giveVoteImpl(CommandContext<CommandSourceStack> ctx, int amount) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = CommandFeedback.refOf(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.playerLookup().findByName(name);
        if (target.isEmpty()) {
            feedback.send(sender, VoteMessageKey.VOTE_TOTAL_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = target.get();
        services.scheduler().async(() -> services.giveVote().give(actor, resolved, amount));
        return Command.SINGLE_SUCCESS;
    }

    private int resetTotals(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = CommandFeedback.refOf(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.playerLookup().findByName(name);
        if (target.isEmpty()) {
            feedback.send(sender, VoteMessageKey.VOTE_TOTAL_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = target.get();
        services.scheduler().async(() -> services.resetVoterTotals().reset(actor, resolved));
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
