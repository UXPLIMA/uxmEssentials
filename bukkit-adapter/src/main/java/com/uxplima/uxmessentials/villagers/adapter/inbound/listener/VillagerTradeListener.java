package com.uxplima.uxmessentials.villagers.adapter.inbound.listener;

import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.PlayerTradeEvent;

import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerTrades;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps a villager's trades usable across the two use-availability features. When a player trades with a villager
 * ({@link PlayerTradeEvent}):
 *
 * <ul>
 *   <li><b>infinite trading</b>. Resets every one of the villager's recipes to zero uses and suppresses the usual
 *       use increment, so no trade ever locks out; and</li>
 *   <li><b>instant restock</b>. Resets just the recipe that was traded and suppresses its increment, so the trade a
 *       player used is available again at once (with no cooldown).</li>
 * </ul>
 *
 * <p>Both suppress the vanilla post-trade use increment ({@code setIncreaseTradeUses(false)}) so the reset performed
 * here is not undone once the event returns. Infinite trading subsumes instant restock, so when both are on the
 * infinite path runs. Only true {@link Villager}s are handled: a wandering trader is out of the module's scope.
 *
 * <h2>Folia</h2>
 * The event is dispatched on the region owning the villager the player is trading with, and the handler only reads and
 * mutates that villager and its recipes on that same thread, so no scheduler hop is needed.
 */
@NullMarked
public final class VillagerTradeListener implements Listener {

    private final boolean infiniteTrading;
    private final boolean instantRestock;

    public VillagerTradeListener(boolean infiniteTrading, boolean instantRestock) {
        this.infiniteTrading = infiniteTrading;
        this.instantRestock = instantRestock;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (!(event.getVillager() instanceof Villager villager)) {
            return;
        }
        if (infiniteTrading) {
            VillagerTrades.restockAll(villager);
            event.setIncreaseTradeUses(false);
        } else if (instantRestock) {
            VillagerTrades.restock(event.getTrade());
            event.setIncreaseTradeUses(false);
        }
    }
}
