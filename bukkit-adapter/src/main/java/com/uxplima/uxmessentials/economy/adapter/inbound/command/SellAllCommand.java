package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.SellAllOutcome;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /sellall}: sell every sellable item in the inventory at its configured worth, the bulk counterpart to
 * {@code /sell}. The pricing, the single-credit, and the per-item tally are the
 * {@link com.uxplima.uxmessentials.economy.application.SellAll} use case's job; this handler snapshots the
 * inventory's materials and counts on the seller's region thread, runs the worth lookups and the credit off the
 * tick thread (so a foreign provider never wedges the command, the same shape {@link SellCommand} uses), and
 * only when the credit actually applied hops back to the seller's region thread to remove the sold stacks, so
 * the inventory and the balance never diverge.
 */
@NullMarked
public final class SellAllCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.sell";

    public SellAllCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("sellall")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Sell every sellable item in your inventory.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Map<Material, Integer> snapshot = snapshot(sender);
        Map<String, Integer> byId = new LinkedHashMap<>();
        snapshot.forEach((material, count) -> byId.put(material.name().toLowerCase(Locale.ROOT), count));
        PlayerRef seller = ref(sender);
        offTick(() -> sellOffTick(seller, byId));
        return Command.SINGLE_SUCCESS;
    }

    private Map<Material, Integer> snapshot(Player sender) {
        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (ItemStack stack : sender.getInventory().getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                totals.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
        }
        return totals;
    }

    private void sellOffTick(PlayerRef seller, Map<String, Integer> byId) {
        SellAllOutcome outcome = services.sellAll().sellAll(seller, byId);
        if (!outcome.sold().isEmpty()) {
            services.scheduler().onEntity(seller, () -> removeItems(seller, outcome.sold()));
        }
    }

    private void removeItems(PlayerRef seller, Map<String, Integer> sold) {
        Player player = Bukkit.getPlayer(seller.uuid());
        if (player == null) {
            return;
        }
        sold.forEach((id, count) -> {
            Material material = Material.matchMaterial(id);
            if (material != null) {
                player.getInventory().removeItem(new ItemStack(material, count));
            }
        });
    }
}
