package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;

import com.uxplima.uxmessentials.survival.application.SurvivalConfig.AnvilUnlocker;
import org.jspecify.annotations.NullMarked;

/**
 * Anvil-unlocker: lifts vanilla's anvil caps so a high-level combine is not rejected with "Too Expensive!". On
 * {@link PrepareAnvilEvent} it raises the anvil's maximum repair cost to remove the level ceiling ({@code
 * remove-level-limit}) and, when {@code remove-cost-limit} is set, zeroes the level price so the unlocked combine is
 * also free. Purely an event tweak. No per-player state, no command; it applies to every anvil while the mechanic is
 * enabled.
 *
 * <p>{@link PrepareAnvilEvent} is not cancellable, so the handler runs at {@link EventPriority#HIGH} to sit after any
 * plugin that computes the result and before the client is shown the view. The maximum-repair-cost lift persists on the
 * anvil view, so the recompute on the next inventory change produces the result the raised cap now allows.
 *
 * <h2>Folia</h2>
 * The event fires on the region owning the open anvil and the handler only mutates that anvil's own view fields, so no
 * scheduler hop is needed.
 */
@NullMarked
public final class AnvilUnlockListener implements Listener {

    private final boolean removeLevelLimit;
    private final boolean removeCostLimit;

    public AnvilUnlockListener(AnvilUnlocker config) {
        Objects.requireNonNull(config, "config");
        this.removeLevelLimit = config.removeLevelLimit();
        this.removeCostLimit = config.removeCostLimit();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        applyTo(event.getView());
    }

    /** Lift the configured caps on {@code view}: the "Too Expensive!" level ceiling and, optionally, the level price. */
    void applyTo(AnvilView view) {
        if (removeLevelLimit) {
            view.setMaximumRepairCost(Integer.MAX_VALUE);
        }
        if (removeCostLimit) {
            view.setRepairCost(0);
        }
    }
}
