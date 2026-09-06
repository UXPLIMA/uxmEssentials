package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpauto}: flip the invoking player's auto-accept switch and confirm the new state. While on, an
 * incoming {@code /tpa} or {@code /tpahere} that clears the toggle/block/self gates is accepted immediately
 * rather than queued: the auto-accept branch lives in {@code RequestTeleport}. The flag itself is owned by the
 * {@code TeleportFlags} port; this command flips it and renders the matching on/off confirmation, the same
 * shape as {@link TpToggleCommand}.
 */
@NullMarked
public final class TpAutoCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tpa.auto";

    public TpAutoCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tpauto")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Auto-accept incoming teleport requests.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef ref = ref(sender);
        boolean nowAuto = services.flags().toggleAutoAccepts(ref);
        TeleportMessageKey key = nowAuto ? TeleportMessageKey.TPAUTO_ON : TeleportMessageKey.TPAUTO_OFF;
        services.notifier().send(ref, key);
        return Command.SINGLE_SUCCESS;
    }
}
