package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.ItemworldHubMenu;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /itemworld gui} ({@code uxmessentials.itemworld.gui}, default op): open the itemworld utilities hub, the
 * launcher of virtual workstations, world time/weather actions, and cleanup sweeps. A players-only command that
 * opens the same {@link ItemworldHubMenu} the {@code /uxmess gui} hub does; it mutates nothing itself, and each
 * launcher button is additionally gated by the permission of the command it stands in for, so a viewer only sees
 * the actions they may already run.
 */
@NullMarked
public final class ItemworldGuiCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.itemworld.gui";

    private final ItemworldHubMenu view;
    private final Messages messages;
    private final MessageSink sink;

    public ItemworldGuiCommand(ItemworldHubMenu view, Messages messages, MessageSink sink) {
        this.view = Objects.requireNonNull(view, "view");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("itemworld")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("gui").executes(this::open))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "Open the itemworld utilities hub.";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            PlayerRef console = PlayerRef.system(sender.getName());
            sink.deliver(console, messages.resolve(console, SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of()));
            return 0;
        }
        view.open(BukkitRefs.toRef(player));
        return Command.SINGLE_SUCCESS;
    }
}
