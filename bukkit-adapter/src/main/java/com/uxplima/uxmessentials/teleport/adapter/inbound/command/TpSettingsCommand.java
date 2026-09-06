package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.adapter.inbound.gui.TeleportSettingsView;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpsettings}: open the per-player teleport settings panel. The accept-requests ({@code /tptoggle}) and
 * auto-accept ({@code /tpauto}) toggles a player flips for themselves. A players-only, self-service command gated
 * by {@code uxmessentials.tpsettings.use}; it opens the same {@link TeleportSettingsView} the {@code /uxmess gui}
 * hub does. It mutates nothing itself. Every flip in the panel routes through the existing {@code TeleportFlags}
 * port the toggle commands already use.
 */
@NullMarked
public final class TpSettingsCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tpsettings.use";

    private final TeleportSettingsView view;

    public TpSettingsCommand(TeleportServices services, Messages messages, TeleportSettingsView view) {
        super(services, messages);
        this.view = Objects.requireNonNull(view, "view");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tpsettings")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "Open your personal teleport settings panel.";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        view.open(sender, ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
