package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /itemdamage <damage>} (alias {@code /durability}): set the held item's durability damage (the
 * inverse of remaining durability). The damage is validated as a non-negative integer by the Brigadier
 * argument and then bounded by the item's max durability. A value above it replies
 * {@link ItemworldMessageKey#ITEMDAMAGE_OUT_OF_RANGE}. An item that cannot take damage (no durability, or a
 * non-{@link Damageable} meta) replies {@link ItemworldMessageKey#ITEMDAMAGE_NOT_DAMAGEABLE}; an empty hand
 * replies {@link ItemworldMessageKey#NO_ITEM_IN_HAND}. The damage write rides the same {@link ItemBuilder}
 * path {@code /repair} uses to reset it.
 */
@NullMarked
public final class ItemDamageCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.itemdamage.use";

    public ItemDamageCommand(ItemworldServices services) {
        super(services, "itemdamage", SubFeatureGroup.ITEM_UTILS, "Set the held item's durability damage.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("damage", IntegerArgumentType.integer(0))
                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "damage"))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public List<String> aliases() {
        return List.of("durability");
    }

    private int run(CommandContext<CommandSourceStack> ctx, int damage) {
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
        apply(ctx, player, held.get(), damage);
        return Command.SINGLE_SUCCESS;
    }

    private void apply(CommandContext<CommandSourceStack> ctx, Player player, ItemStack hand, int damage) {
        ItemMeta meta = hand.getItemMeta();
        short max = hand.getType().getMaxDurability();
        if (max <= 0 || !(meta instanceof Damageable)) {
            reply(ctx, ItemworldMessageKey.ITEMDAMAGE_NOT_DAMAGEABLE);
            return;
        }
        if (damage > max) {
            reply(ctx, ItemworldMessageKey.ITEMDAMAGE_OUT_OF_RANGE, Map.of("max", String.valueOf(max)));
            return;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            ItemStack updated = ItemBuilder.from(hand).damage(damage).build();
            player.getInventory().setItemInMainHand(updated);
            reply(ctx, ItemworldMessageKey.ITEMDAMAGE_SET, Map.of("damage", String.valueOf(damage)));
        });
    }
}
