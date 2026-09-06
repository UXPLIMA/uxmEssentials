package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.UnlimitedPlacementStore;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /unlimited}: toggle unlimited block placement for yourself, when on, the block-place refill listener
 * tops the placed stack back up so it never empties (the standard {@code /unlimited} behaviour). The state is a transient
 * per-player flag; the new value is reported through {@link ItemworldMessageKey#UNLIMITED_ENABLED} /
 * {@link ItemworldMessageKey#UNLIMITED_DISABLED}.
 */
@NullMarked
public final class UnlimitedCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.unlimited.use";

    private final UnlimitedPlacementStore store;

    public UnlimitedCommand(ItemworldServices services, UnlimitedPlacementStore store) {
        super(services, "unlimited", SubFeatureGroup.MOB_ENTITY, "Toggle unlimited block placement.");
        this.store = java.util.Objects.requireNonNull(store, "store");
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
        boolean nowOn = store.toggle(who);
        reply(ctx, nowOn ? ItemworldMessageKey.UNLIMITED_ENABLED : ItemworldMessageKey.UNLIMITED_DISABLED);
        return Command.SINGLE_SUCCESS;
    }
}
