package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.adapter.inbound.gui.PresenceSettingsView;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /presencesettings}: open the per-player presence settings panel, the AFK ({@code /afk}) and vanish
 * ({@code /vanish}) toggles a player flips for themselves. A players-only, self-service command gated by
 * {@code uxmessentials.presencesettings.use}; it opens the same {@link PresenceSettingsView} the
 * {@code /uxmess gui} hub does. It mutates nothing itself. Every flip routes through the existing
 * {@code MarkAfk} / {@code ToggleVanish} use cases the toggle commands already use.
 */
@NullMarked
public final class PresenceSettingsCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.presencesettings.use";

    private final PresenceSettingsView view;

    public PresenceSettingsCommand(
            PresenceServices services, Messages messages, Scheduler scheduler, PresenceSettingsView view) {
        super(services, messages, scheduler);
        this.view = Objects.requireNonNull(view, "view");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("presencesettings")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "Open your personal presence settings panel.";
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
