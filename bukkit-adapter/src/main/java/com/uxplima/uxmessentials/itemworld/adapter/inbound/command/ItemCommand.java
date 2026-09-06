package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitItemResolver;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.AmountSpec;
import com.uxplima.uxmessentials.itemworld.domain.ItemQuery;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /item <item> [amount]}: give an item to yourself. Cosmetic self-give, not an abusable verb, so no
 * audit line, but the same boundary validation as {@code /give}: the item id resolves to an obtainable
 * {@link Material} (else {@link ItemworldMessageKey#UNKNOWN_ITEM}) and the amount validates against the
 * {@code give-cap} (else {@link ItemworldMessageKey#AMOUNT_OUT_OF_RANGE}) before any stack materialises.
 */
@NullMarked
public final class ItemCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.item.use";

    public ItemCommand(ItemworldServices services) {
        super(services, "item", SubFeatureGroup.ITEM_UTILS, "Give yourself an item.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(itemArgument()
                        .executes(ctx -> run(ctx, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, int requestedAmount) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        String raw = ctx.getArgument("item", String.class);
        Optional<Material> material = ItemQuery.parse(raw).flatMap(BukkitItemResolver::material);
        if (material.isEmpty()) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_ITEM, Map.of("item", raw));
            return Command.SINGLE_SUCCESS;
        }
        Optional<AmountSpec> amount =
                AmountSpec.of(requestedAmount, services.config().giveCap());
        if (amount.isEmpty()) {
            reply(
                    ctx,
                    ItemworldMessageKey.AMOUNT_OUT_OF_RANGE,
                    Map.of("cap", String.valueOf(services.config().giveCap())));
            return Command.SINGLE_SUCCESS;
        }
        give(ctx, player, material.get(), amount.get());
        return Command.SINGLE_SUCCESS;
    }

    private void give(CommandContext<CommandSourceStack> ctx, Player player, Material material, AmountSpec amount) {
        String itemKey = material.getKey().toString();
        services.kernel().scheduler().onEntity(ref(player), () -> {
            player.getInventory().addItem(new ItemStack(material, amount.amount()));
            reply(
                    ctx,
                    ItemworldMessageKey.ITEM_GIVEN,
                    Map.of("item", itemKey, "amount", String.valueOf(amount.amount())));
        });
    }
}
