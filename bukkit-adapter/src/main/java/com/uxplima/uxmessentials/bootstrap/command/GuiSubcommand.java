package com.uxplima.uxmessentials.bootstrap.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementHubView;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /uxmess gui} subcommand: opens the config-driven management hub listing every module's
 * registered management-GUI entry the viewer may use, each a clickable icon launching that module's GUI.
 *
 * <p>Attached under the {@code /uxmess} root (alongside {@code status}/{@code doctor}/{@code reload}), it
 * gates on its own {@code uxmessentials.gui} node. The per-module entries inside the hub gate further on
 * each module's own {@code uxmessentials.<module>.gui} node, resolved through the shared {@link Permissions}
 * port when the hub filters its contents. The command is player-only (a hub is a screen): a console source
 * gets the shared players-only reply, and a viewer who is permitted no entries gets the {@code gui.hub.empty}
 * line rather than a blank menu.
 */
@NullMarked
public final class GuiSubcommand {

    private static final String PERMISSION_GUI = "uxmessentials.gui";

    private final ManagementGuiRegistry registry;
    private final ManagementHubView hub;
    private final Permissions permissions;
    private final CommandFeedback feedback;

    public GuiSubcommand(
            ManagementGuiRegistry registry, ManagementHubView hub, Permissions permissions, Messages messages) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.hub = Objects.requireNonNull(hub, "hub");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** The {@code gui} literal node, gated on {@code uxmessentials.gui}, for attaching under {@code /uxmess}. */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("gui")
                .requires(src -> src.getSender().hasPermission(PERMISSION_GUI))
                .executes(this::runGui);
    }

    /**
     * The bare-input opener for {@code /uxmess}: opens the same management hub the {@code gui} subcommand
     * does, so an operator who runs {@code /uxmess} with no arguments lands on the hub when the command's
     * catalog {@code gui} flag is on. Shares one open path so the two never drift.
     */
    public Command<CommandSourceStack> opener() {
        return this::runGui;
    }

    private int runGui(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of());
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        if (registry.entriesFor(viewer, permissions).isEmpty()) {
            feedback.send(sender, GuiMessageKey.HUB_EMPTY, Map.of());
            return Command.SINGLE_SUCCESS;
        }
        hub.open(player, viewer);
        return Command.SINGLE_SUCCESS;
    }
}
