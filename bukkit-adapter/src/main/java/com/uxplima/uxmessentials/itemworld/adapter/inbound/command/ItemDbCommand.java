package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitItemResolver;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.ItemQuery;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /itemdb [item]}: look up an item's id and max stack size. The held item when called with no argument,
 * or a named item resolved against the registry. Read-only, so no audit and no region hop is needed; the
 * report is rendered through {@link ItemworldMessageKey#ITEMDB_REPORT}. An unknown named item replies
 * {@link ItemworldMessageKey#UNKNOWN_ITEM}; an empty hand with no argument replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 */
@NullMarked
public final class ItemDbCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.itemdb.use";

    public ItemDbCommand(ItemworldServices services) {
        super(services, "itemdb", SubFeatureGroup.ITEM_UTILS, "Look up an item's id.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(Commands.argument("item", StringArgumentType.word())
                        .executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "item")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> named) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Material> material = named.isPresent() ? namedItem(ctx, named.get()) : heldMaterial(ctx);
        material.ifPresent(found -> report(ctx, found));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<Material> namedItem(CommandContext<CommandSourceStack> ctx, String raw) {
        Optional<Material> material = ItemQuery.parse(raw).flatMap(BukkitItemResolver::material);
        if (material.isEmpty()) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_ITEM, Map.of("item", raw));
        }
        return material;
    }

    private Optional<Material> heldMaterial(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            return Optional.empty();
        }
        return heldItem(ctx, player).map(ItemStack::getType);
    }

    private void report(CommandContext<CommandSourceStack> ctx, Material material) {
        reply(
                ctx,
                ItemworldMessageKey.ITEMDB_REPORT,
                Map.of("item", material.getKey().toString(), "max", String.valueOf(material.getMaxStackSize())));
    }
}
