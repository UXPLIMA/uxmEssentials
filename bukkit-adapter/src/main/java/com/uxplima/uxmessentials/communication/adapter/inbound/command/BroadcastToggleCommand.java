package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /broadcasttoggle} ({@code uxmessentials.communication.broadcasttoggle}): flip whether the invoking player
 * receives announcer broadcasts. The {@code BroadcastOptOut} use case owns the PDC-backed bit flip, the
 * confirmation (one of the plugin's own {@code CommunicationMessageKey} strings through the {@code MessageSink}),
 * and the {@code BroadcastOptOutToggled} event; this handler only maps the invoking player. The static surface,
 * the plugin's own string: distinct from the operator-authored announcer lines the toggle governs.
 */
@NullMarked
public final class BroadcastToggleCommand extends CommunicationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.communication.broadcasttoggle";

    private final BroadcastOptOut optOut;

    public BroadcastToggleCommand(BroadcastOptOut optOut, Messages messages) {
        super(messages);
        this.optOut = Objects.requireNonNull(optOut, "optOut");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("broadcasttoggle")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .build();
    }

    @Override
    public String description() {
        return "Toggle whether you receive server announcements.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        optOut.toggle(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
