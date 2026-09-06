package com.uxplima.uxmessentials.villagers.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import org.jspecify.annotations.NullMarked;

/**
 * Opens a villager's trade window on a right-click for a permitted player, even where the vanilla gate would refuse it
 * (a professionless villager, or one already claimed by another trader). The player must hold
 * {@code uxmessentials.villagers.trade}, and a disabled villager. By the global {@code disable-trades} switch or its
 * per-villager flag: never opens, so the click-to-trade feature never overrides a trade the operator turned off.
 *
 * <p>When the listener opens the window it cancels the interaction so the vanilla open does not also fire; when it
 * declines (no permission, or disabled) it leaves the event untouched for the disable listener to handle. The open is
 * routed through the injected {@link TradeOpener} seam so the actual {@code openMerchant} call is isolated from the
 * gate decision.
 *
 * <h2>Folia</h2>
 * The interaction is dispatched on the region owning the clicked villager; the player is right there interacting with
 * it, so the trade window is opened on that same thread with no scheduler hop.
 */
@NullMarked
public final class ClickToTradeListener implements Listener {

    private final boolean disableGlobally;
    private final String tradePermission;
    private final PdcVillagerFlags flags;
    private final TradeOpener opener;

    public ClickToTradeListener(
            boolean disableGlobally, String tradePermission, PdcVillagerFlags flags, TradeOpener opener) {
        this.disableGlobally = disableGlobally;
        this.tradePermission = Objects.requireNonNull(tradePermission, "tradePermission");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        if (!shouldOpen(event.getPlayer(), villager)) {
            return;
        }
        event.setCancelled(true);
        opener.open(event.getPlayer(), villager);
    }

    /** Whether a right-click should open {@code villager}'s trade window for {@code player}. */
    boolean shouldOpen(Player player, Villager villager) {
        if (!player.hasPermission(tradePermission)) {
            return false;
        }
        return !(disableGlobally || flags.tradesDisabled(villager));
    }

    /** The seam that actually shows a villager's trade window; production opens the live merchant. */
    @FunctionalInterface
    public interface TradeOpener {

        /** Open {@code villager}'s trade window for {@code player}. */
        void open(Player player, Villager villager);
    }
}
