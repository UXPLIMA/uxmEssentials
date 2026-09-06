package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PowertoolToggleStore;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /powertooltoggle}: flip whether your powertool bindings fire on interact, without re-binding any item.
 * The bindings stamped onto items stay put, only the per-player gate the interact listener consults changes,
 * so a player can silence and re-enable their tools at will. The new state is reported through
 * {@link ItemworldMessageKey#POWERTOOL_ENABLED} / {@link ItemworldMessageKey#POWERTOOL_DISABLED}.
 */
@NullMarked
public final class PowertoolToggleCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.powertool.toggle";

    private final PowertoolToggleStore toggles;

    public PowertoolToggleCommand(ItemworldServices services, PowertoolToggleStore toggles) {
        super(services, "powertooltoggle", SubFeatureGroup.POWERTOOL, "Toggle your powertool bindings.");
        this.toggles = java.util.Objects.requireNonNull(toggles, "toggles");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef who = ref(player);
        boolean nowEnabled = toggles.toggle(who);
        reply(ctx, nowEnabled ? ItemworldMessageKey.POWERTOOL_ENABLED : ItemworldMessageKey.POWERTOOL_DISABLED);
        return Command.SINGLE_SUCCESS;
    }
}
