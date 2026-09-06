package com.uxplima.uxmessentials.survival.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.survival.adapter.inbound.gui.SurvivalSettingsView;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /survival}: open the personal survival settings panel. One row per toggleable mechanic the player may switch
 *: gated by the self-service {@code uxmessentials.survival.gui}. It consolidates the per-mechanic toggle commands
 * ({@code /treefeller}, {@code /veinminer}, …) into one screen; those commands still work, and every flip the panel
 * makes is the same PDC write they make. A non-player sender gets the players-only notice.
 */
@NullMarked
public final class SurvivalCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.survival.gui";

    private final SurvivalSettingsView settingsView;
    private final CommandFeedback feedback;

    public SurvivalCommand(SurvivalSettingsView settingsView, Messages messages) {
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("survival")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "Open your survival panel: toggle tree-feller, veinminer, auto-pickup and the rest.";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        settingsView.open(player, BukkitRefs.toRef(player));
        return Command.SINGLE_SUCCESS;
    }
}
