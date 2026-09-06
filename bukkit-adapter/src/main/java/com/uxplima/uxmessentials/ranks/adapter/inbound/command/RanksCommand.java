package com.uxplima.uxmessentials.ranks.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.ranks.adapter.inbound.gui.RanksPanelMenu;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /ranks}: the ranks context's root command. It carries the admin {@code /ranks setrank <player> <rank>}
 * subcommand. The escape hatch that sets a player's rank pointer directly through {@link SetRank}, bypassing the
 * requirements, cost and actions {@code /rankup} runs (the player argument is a standard selector resolving to an
 * online target; the rank argument completes against the ladder's rank ids and is refused when it names no rung).
 * {@code setrank} is gated on {@code uxmessentials.ranks.admin}.
 *
 * <p>When the ladder GUI is enabled ({@code modules.ranks.gui.enabled}), a bare {@code /ranks} opens the
 * {@link RanksPanelMenu ladder panel}. The player's current rank/prestige, the next rank's requirements and cost,
 * and a rank-up button: gated on the self-service {@code uxmessentials.ranks.gui}. The panel is injected as an
 * {@link Optional}: when GUI is off, the no-argument open is not wired and the root gates on admin alone, so a
 * disabled GUI publishes exactly the {@code setrank}-only surface it always did.
 */
@NullMarked
public final class RanksCommand implements CommandRegistration {

    /** The permission an administrator holds to set a player's rank directly. */
    public static final String PERMISSION = "uxmessentials.ranks.admin";

    /** The self-service permission a player holds to open the {@code /ranks} ladder panel. */
    public static final String GUI_PERMISSION = "uxmessentials.ranks.gui";

    private final SetRankExecutor setRank;
    private final Optional<RanksPanelMenu> panel;
    private final CommandFeedback feedback;

    public RanksCommand(SetRank setRank, RankLadder ladder, Optional<RanksPanelMenu> panel, Messages messages) {
        this.setRank = new SetRankExecutor(
                Objects.requireNonNull(setRank, "setRank"), Objects.requireNonNull(ladder, "ladder"), messages);
        this.panel = Objects.requireNonNull(panel, "panel");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ranks")
                .requires(src -> src.getSender().hasPermission(PERMISSION)
                        || (panel.isPresent() && src.getSender().hasPermission(GUI_PERMISSION)))
                .then(Commands.literal("setrank")
                        .requires(src -> src.getSender().hasPermission(PERMISSION))
                        .then(setRank.arguments()));
        // The no-argument ladder panel is wired only when the GUI is enabled; when off, /ranks stays setrank-only.
        panel.ifPresent(view -> root.executes(this::openGui));
        return root.build();
    }

    @Override
    public String description() {
        return panel.isPresent()
                ? "/ranks to open the ladder panel; /ranks setrank <player> <rank> to set a player's rank directly."
                : "/ranks setrank <player> <rank> to set a player's rank directly.";
    }

    /** Open the ladder panel for a player who holds the GUI permission; a non-player sender gets the players-only line. */
    private int openGui(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        panel.orElseThrow().open(player, BukkitRefs.toRef(player));
        return Command.SINGLE_SUCCESS;
    }
}
