package com.uxplima.uxmessentials.ranks.adapter.inbound.command;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.ranks.application.Prestige;
import com.uxplima.uxmessentials.ranks.application.PrestigeResult;
import com.uxplima.uxmessentials.ranks.application.RanksMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /prestige}: a player at the top of the ladder resets back to the first rank for a permanent prestige
 * level. The handler resolves only the player's own identity and hands it to {@link Prestige}, which owns the
 * eligibility / requirement / cost / action pipeline and returns a typed {@link PrestigeResult}; this class maps
 * that outcome to the matching catalog line. The success line carrying the new prestige level as {@code {prestige}}
 * and the earned reward multiplier as {@code {multiplier}}. Gated on {@code uxmessentials.ranks.prestige}, which
 * ships {@code true} so every player may prestige; the command is only registered at all when the prestige
 * mechanic is enabled in {@code modules/ranks/config.conf}.
 */
@NullMarked
public final class PrestigeCommand implements CommandRegistration {

    /** The permission every player holds by default so they can prestige once they reach the top rank. */
    public static final String PERMISSION = "uxmessentials.ranks.prestige";

    private final Prestige prestige;
    private final CommandFeedback feedback;

    public PrestigeCommand(Prestige prestige, Messages messages) {
        this.prestige = Objects.requireNonNull(prestige, "prestige");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("prestige")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "/prestige to reset to the first rank for a prestige level once you reach the top rank.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        render(player, prestige.prestige(who));
        return Command.SINGLE_SUCCESS;
    }

    private void render(Player player, PrestigeResult result) {
        switch (result.status()) {
            case PRESTIGED ->
                feedback.send(
                        player,
                        RanksMessageKey.RANKS_PRESTIGE_SUCCESS,
                        Map.of(
                                "prestige", String.valueOf(result.newLevel()),
                                "multiplier", format(result.rewardMultiplier())));
            case NOT_AT_TOP -> feedback.send(player, RanksMessageKey.RANKS_PRESTIGE_NOT_MAX_RANK);
            case MAX_LEVEL -> feedback.send(player, RanksMessageKey.RANKS_PRESTIGE_MAX);
            case REQUIREMENTS_NOT_MET -> feedback.send(player, RanksMessageKey.RANKS_PRESTIGE_REQUIREMENTS);
            case CANNOT_AFFORD -> feedback.send(player, RanksMessageKey.RANKS_PRESTIGE_COST);
        }
    }

    /** Render the multiplier without trailing zeros so {@code 1.50} shows as {@code 1.5} and {@code 2.0} as {@code 2}. */
    private static String format(double multiplier) {
        return BigDecimal.valueOf(multiplier).stripTrailingZeros().toPlainString();
    }
}
