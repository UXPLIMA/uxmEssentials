package com.uxplima.uxmessentials.trade.adapter.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeItemDelivery;
import org.jspecify.annotations.NullMarked;

/**
 * Bukkit implementation of {@link TradeItemDelivery}: it decodes a cross-server trade's escrowed item string and hands
 * the stacks to the recipient on their own region thread (Folia-safe), adding them to the inventory and dropping any
 * overflow at their feet so no stack is ever lost. The coordinator only asks to deliver items to a recipient it has
 * checked is online, and defers an offline recipient to their next join, so this never has to hold items for an absent
 * player, but if the player logs off in the sub-tick between the check and the region hop, the overflow-to-ground path
 * still keeps the items in the world.
 */
@NullMarked
public final class BukkitTradeItemDelivery implements TradeItemDelivery {

    private final Scheduler scheduler;

    public BukkitTradeItemDelivery(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean isOnline(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Player live = Bukkit.getPlayer(player.uuid());
        return live != null && live.isOnline();
    }

    @Override
    public void deliver(PlayerRef player, String itemData) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(itemData, "itemData");
        if (itemData.isBlank()) {
            return;
        }
        List<ItemStack> gifts = TradeItemBytes.decode(itemData);
        if (gifts.isEmpty()) {
            return;
        }
        scheduler.onEntity(player, () -> {
            Player live = Bukkit.getPlayer(player.uuid());
            if (live == null || !live.isOnline()) {
                return;
            }
            Map<Integer, ItemStack> overflow = live.getInventory().addItem(gifts.toArray(ItemStack[]::new));
            for (ItemStack extra : overflow.values()) {
                live.getWorld().dropItemNaturally(live.getLocation(), extra);
            }
        });
    }
}
