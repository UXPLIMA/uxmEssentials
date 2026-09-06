package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /repair}: repair the held item, reset its durability damage to zero. Owned here in the itemworld
 * surface (it shares the playerstate repair semantics; both nodes gate the same action, docs/permissions.md
 * §Itemworld), gated by the {@code uxmessentials.repair.itemworld} node. A held item with no durability damage
 * (or a non-damageable item) replies {@link ItemworldMessageKey#REPAIR_NOTHING}; an empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 */
@NullMarked
public final class RepairCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.repair.itemworld";

    public RepairCommand(ItemworldServices services) {
        super(services, "repair", SubFeatureGroup.ITEM_UTILS, "Repair the held item.");
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
        Optional<ItemStack> held = heldItem(ctx, player);
        if (held.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        repair(ctx, player, held.get());
        return Command.SINGLE_SUCCESS;
    }

    private void repair(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand) {
        ItemMeta meta = hand.getItemMeta();
        if (!(meta instanceof Damageable damageable) || !damageable.hasDamage()) {
            reply(ctx, ItemworldMessageKey.REPAIR_NOTHING);
            return;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            ItemStack repaired = ItemBuilder.from(hand).damage(0).build();
            player.getInventory().setItemInMainHand(repaired);
            reply(ctx, ItemworldMessageKey.REPAIR_DONE);
        });
    }
}
