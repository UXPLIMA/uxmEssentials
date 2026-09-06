package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
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
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /giveall <item> [amount]}: hand an item to every online player at once. The bulk sibling of
 * {@code /give}, gated on its own {@code uxmessentials.giveall.use} node so a network can grant the
 * server-wide handout independently of the per-target give (docs/permissions.md §Itemworld,
 * docs/10-feature-modules.md §15.10).
 *
 * <p>Inputs are validated at the boundary exactly as {@code /give}: the item id parses to an {@link ItemQuery}
 * and resolves to an obtainable {@link Material} (else {@link ItemworldMessageKey#UNKNOWN_ITEM}); the amount
 * validates to an {@link AmountSpec} within the {@code give-cap} (else
 * {@link ItemworldMessageKey#AMOUNT_OUT_OF_RANGE}) before any stack materialises. Each recipient's stack is
 * added on that player's own region thread through the kernel {@code Scheduler}, and, being a bulk handout,
 * each delivery is audit-logged when the amount crosses the configured threshold.
 */
@NullMarked
public final class GiveAllCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.giveall.use";

    public GiveAllCommand(ItemworldServices services) {
        super(services, "giveall", SubFeatureGroup.ITEM_UTILS, "Give an item to every online player.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> usage(ctx, "giveall", "<item> [amount]", describe()))
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

    @Override
    public List<String> aliases() {
        return List.of();
    }

    private int run(CommandContext<CommandSourceStack> ctx, int requestedAmount) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Material> material = resolveItem(ctx);
        if (material.isEmpty()) {
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
        distribute(ctx, material.get(), amount.get());
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

    private void distribute(CommandContext<CommandSourceStack> ctx, Material material, AmountSpec amount) {
        PlayerRef actor = actorOf(ctx);
        String itemKey = material.getKey().toString();
        boolean audit = amount.amount() >= services.config().giveAuditThreshold();
        // The online roster is enumerated on the global region thread (Folia forbids iterating
        // Bukkit.getOnlinePlayers() off it); each recipient's stack is then added on that player's own region thread.
        services.kernel().scheduler().onGlobal(() -> {
            int recipients = 0;
            for (Player target : ctx.getSource().getSender().getServer().getOnlinePlayers()) {
                deliverTo(actor, BukkitRefs.toRef(target), material, amount, itemKey, audit);
                recipients++;
            }
            reply(ctx, ItemworldMessageKey.GIVEALL_DONE, placeholders(itemKey, amount.amount(), recipients));
        });
    }

    private void deliverTo(
            PlayerRef actor, PlayerRef targetRef, Material material, AmountSpec amount, String itemKey, boolean audit) {
        services.kernel().scheduler().onEntity(targetRef, () -> {
            Player live = Bukkit.getPlayer(targetRef.uuid());
            if (live != null && live.isOnline()) {
                live.getInventory().addItem(new ItemStack(material, amount.amount()));
            }
        });
        if (audit) {
            services.audit().gave(actor, targetRef, itemKey, amount.amount());
        }
    }

    private PlayerRef actorOf(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender() instanceof Player player
                ? BukkitRefs.toRef(player)
                : PlayerRef.system(ctx.getSource().getSender().getName());
    }

    private static Map<String, String> placeholders(String item, int amount, int count) {
        return Map.of("item", item, "amount", String.valueOf(amount), "count", String.valueOf(count));
    }
}
