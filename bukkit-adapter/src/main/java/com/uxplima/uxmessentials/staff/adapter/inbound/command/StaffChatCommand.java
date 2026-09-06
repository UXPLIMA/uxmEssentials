package com.uxplima.uxmessentials.staff.adapter.inbound.command;

import java.util.List;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.staff.adapter.StaffServices;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /staffchat <message>} (alias {@code /sc}, {@code uxmessentials.staff.chat}): send one line on the
 * staff-only channel. The {@code SendStaffChat} use case owns the fan-out to every online staff-chat holder and
 * the audit event; this handler only maps the sender and the greedy message argument. Staff chat carries no
 * sanction: it is plain communication.
 */
@NullMarked
public final class StaffChatCommand extends StaffCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.staff.chat";

    public StaffChatCommand(StaffServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("staffchat")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(this::send))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("sc");
    }

    @Override
    public String description() {
        return "Send a message on the staff channel.";
    }

    private int send(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String message = ctx.getArgument("message", String.class);
        services.staffChat().send(ref(sender), message);
        return Command.SINGLE_SUCCESS;
    }
}
