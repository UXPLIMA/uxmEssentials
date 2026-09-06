package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /iteminfo}: inspect the item in your main hand. Its material id, display name, enchantments, custom
 * model data and durability. Read-only (no audit, no region hop, never mutates the item), the held-item
 * inspector players expect alongside {@code /itemdb}. The report is two
 * {@link ItemworldMessageKey} lines, a header and a body line carrying every field; an empty hand replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 */
@NullMarked
public final class ItemInfoCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.iteminfo.use";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public ItemInfoCommand(ItemworldServices services) {
        super(services, "iteminfo", SubFeatureGroup.ITEM_UTILS, "Inspect the item you are holding.");
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
        held.ifPresent(item -> report(ctx, item));
        return Command.SINGLE_SUCCESS;
    }

    private void report(CommandContext<CommandSourceStack> ctx, ItemStack item) {
        reply(
                ctx,
                ItemworldMessageKey.ITEMINFO_HEADER,
                Map.of("item", item.getType().getKey().toString()));
        reply(
                ctx,
                ItemworldMessageKey.ITEMINFO_LINE,
                Map.of(
                        "item", item.getType().getKey().toString(),
                        "amount", Integer.toString(item.getAmount()),
                        "name", displayName(item),
                        "enchants", enchantments(item),
                        "model", customModelData(item),
                        "durability", durability(item)));
    }

    private static String displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.displayName() == null) {
            return "-";
        }
        return PLAIN.serialize(java.util.Objects.requireNonNull(meta.displayName()));
    }

    private static String enchantments(ItemStack item) {
        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (enchants.isEmpty()) {
            return "-";
        }
        return enchants.entrySet().stream()
                .map(e -> e.getKey().getKey().getKey() + " " + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static String customModelData(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "-";
        }
        java.util.List<Float> floats = meta.getCustomModelDataComponent().getFloats();
        if (floats.isEmpty()) {
            return "-";
        }
        return floats.stream().map(value -> Integer.toString(value.intValue())).collect(Collectors.joining(", "));
    }

    private static String durability(ItemStack item) {
        short max = item.getType().getMaxDurability();
        if (max <= 0 || !(item.getItemMeta() instanceof Damageable damageable)) {
            return "-";
        }
        return (max - damageable.getDamage()) + "/" + max;
    }
}
