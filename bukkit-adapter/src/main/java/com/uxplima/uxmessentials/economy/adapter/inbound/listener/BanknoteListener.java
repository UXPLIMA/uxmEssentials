package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Listens to players right-clicking physical banknotes to deposit them back into their virtual account. The
 * interaction fires on the clicking player's region thread, so the redemption, which mutates the held item,
 * runs inline through the shared {@link BanknoteRedeemer}, which owns the dupe-safe ordering and the per-token
 * in-flight guard that keeps this listener and {@code /deposit} from both consuming one note on a tick.
 */
@NullMarked
public final class BanknoteListener implements Listener {

    private final BanknoteRedeemer redeemer;

    public BanknoteListener(BanknoteRedeemer redeemer) {
        this.redeemer = Objects.requireNonNull(redeemer, "redeemer");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER || !redeemer.isBanknote(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        PlayerRef owner = BukkitRefs.toRef(player);
        redeemer.redeem(player, owner, item);
    }
}
