package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.communication.adapter.inbound.gui.CommunicationAdminMenu;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /communication gui} ({@code uxmessentials.communication.gui}, default op): open the communication admin
 * panel: the chat-lock toggle, the clearchat and broadcast actions, and the read-only announcer list. A
 * players-only, admin command that opens the same {@link CommunicationAdminMenu} the {@code /uxmess gui} hub does;
 * it mutates nothing itself, routing every action through the surfaces the communication commands already use.
 */
@NullMarked
public final class CommunicationGuiCommand extends CommunicationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.communication.gui";

    private final CommunicationAdminMenu menu;

    public CommunicationGuiCommand(CommunicationAdminMenu menu, Messages messages) {
        super(messages);
        this.menu = Objects.requireNonNull(menu, "menu");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("communication")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("gui").executes(this::open))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "Open the communication admin panel.";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        menu.open(ref(sender));
        return Command.SINGLE_SUCCESS;
    }
}
