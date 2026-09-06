package com.uxplima.uxmessentials.trade.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.trade.application.CrossServerTrade;
import org.jspecify.annotations.NullMarked;

/**
 * Reconciles a rejoining player's dangling cross-server trade escrow. A player who logged off (or whose backend
 * crashed) mid-trade left an escrow row behind; on their next join this asks the {@link CrossServerTrade} coordinator to
 * settle it (deliver the counterpart's goods when both sides escrowed) or refund it (return the stake when the
 * counterpart never did): the crash-safety guarantee the escrow row exists for. The reconcile runs off the tick
 * thread through the injected {@link Scheduler}, and the coordinator hops to the player's region for any delivery.
 */
@NullMarked
public final class TradeReconcileListener implements Listener {

    private final Scheduler scheduler;
    private final CrossServerTrade coordinator;

    public TradeReconcileListener(Scheduler scheduler, CrossServerTrade coordinator) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        java.util.UUID player = event.getPlayer().getUniqueId();
        scheduler.async(() -> coordinator.reconcile(player));
    }
}
