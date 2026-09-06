package com.uxplima.uxmessentials.villagers.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.application.VillagersMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Refuses the trade GUI of a disabled villager: a right-click on the villager with the main hand is cancelled and the
 * player is told why. A villager counts as disabled when the global {@code disable-trades} switch is on (every villager
 * is disabled) <em>or</em> when the individual villager carries the per-villager disable flag the Phase-2 trade manager
 * sets. The per-villager flag is honoured whether or not the global switch is on, which is why this listener registers
 * for the whole module rather than only under the global switch, with the switch off and no flag it is a plain no-op
 * on every villager interaction.
 *
 * <h2>Folia</h2>
 * The interaction is dispatched on the region owning the clicked villager; the handler only reads the villager's PDC
 * and cancels the event on that same thread. The player notice is sent through {@link CommandFeedback}, which resolves
 * and delivers in the player's own locale.
 */
@NullMarked
public final class DisableTradesListener implements Listener {

    private final boolean disableGlobally;
    private final PdcVillagerFlags flags;
    private final CommandFeedback feedback;

    public DisableTradesListener(boolean disableGlobally, PdcVillagerFlags flags, Messages messages) {
        this.disableGlobally = disableGlobally;
        this.flags = Objects.requireNonNull(flags, "flags");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        if (disableGlobally || flags.tradesDisabled(villager)) {
            event.setCancelled(true);
            feedback.send(event.getPlayer(), VillagersMessageKey.VILLAGERS_TRADES_DISABLED);
        }
    }
}
