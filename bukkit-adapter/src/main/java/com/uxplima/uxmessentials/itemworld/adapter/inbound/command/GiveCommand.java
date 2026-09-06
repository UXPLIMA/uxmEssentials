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
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /give <player> <item> [amount]} (alias {@code /i}): give an item to a player. The abusable verb of
 * the item-utils group. A bulk give is audit-logged when the amount crosses the configured threshold, and
 * the amount is validated against the {@code give-cap} before any stack materialises (docs/permissions.md
 * §Itemworld, docs/10-feature-modules.md §15.10).
 *
 * <p>Inputs are validated at the boundary in order: the item id parses to an {@link ItemQuery} and resolves to
 * an obtainable {@link Material} (else {@link ItemworldMessageKey#UNKNOWN_ITEM}); the amount validates to an
 * {@link AmountSpec} within the cap (else {@link ItemworldMessageKey#AMOUNT_OUT_OF_RANGE}); the target resolves
 * online (else {@link ItemworldMessageKey#UNKNOWN_TARGET}). Only then is the stack added, scheduled onto the
 * target's region thread through the kernel {@code Scheduler}, and the give audit-logged.
 */
@NullMarked
public final class GiveCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.give.use";

    public GiveCommand(ItemworldServices services) {
        super(services, "give", SubFeatureGroup.ITEM_UTILS, "Give an item to a player.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> usage(ctx, "give", "<player> <item> [amount]", describe()))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> usage(ctx, "give", "<player> <item> [amount]", describe()))
                        .then(itemArgument()
                                .executes(ctx -> run(ctx, 1))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public java.util.List<String> aliases() {
        return java.util.List.of("i");
    }

    private int run(CommandContext<CommandSourceStack> ctx, int requestedAmount) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Material> material = resolveItem(ctx);
        if (material.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        if (!allowsItem(ctx, material.get())) {
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
        deliver(ctx, material.get(), amount.get());
        return Command.SINGLE_SUCCESS;
    }

    private Optional<Material> resolveItem(CommandContext<CommandSourceStack> ctx) {
        String raw = ctx.getArgument("item", String.class);
        Optional<Material> material = ItemQuery.parse(raw).flatMap(BukkitItemResolver::material);
        if (material.isEmpty()) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_ITEM, Map.of("item", raw));
        }
        return material;
    }

    /**
     * Per-type gate on the resolved item, modelled on the common per-item {@code give} permission scheme;
     * the node here is {@code uxmessentials.itemworld.give.<material>}. The check binds to the sender, so a
     * console source (which has already passed the base node and carries no per-player nodes) is allowed every type.
     */
    private boolean allowsItem(CommandContext<CommandSourceStack> ctx, Material material) {
        return !(ctx.getSource().getSender() instanceof Player sender)
                || allowsType(ctx, sender, "give", material.getKey().getKey());
    }

    private void deliver(CommandContext<CommandSourceStack> ctx, Material material, AmountSpec amount) {
        String name = ctx.getArgument("player", String.class);
        Player target = ctx.getSource().getSender().getServer().getPlayerExact(name);
        if (target == null) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_TARGET, Map.of("player", name));
            return;
        }
        PlayerRef actor = actorOf(ctx);
        PlayerRef targetRef = BukkitRefs.toRef(target);
        String itemKey = material.getKey().toString();
        services.kernel().scheduler().onEntity(targetRef, () -> {
            target.getInventory().addItem(new ItemStack(material, amount.amount()));
            reply(ctx, ItemworldMessageKey.GIVE_GIVEN, placeholders(name, itemKey, amount.amount()));
        });
        if (amount.amount() >= services.config().giveAuditThreshold()) {
            services.audit().gave(actor, targetRef, itemKey, amount.amount());
        }
    }

    private PlayerRef actorOf(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender() instanceof Player player
                ? BukkitRefs.toRef(player)
                : PlayerRef.system(ctx.getSource().getSender().getName());
    }

    private static Map<String, String> placeholders(String player, String item, int amount) {
        return Map.of("player", player, "item", item, "amount", String.valueOf(amount));
    }
}
