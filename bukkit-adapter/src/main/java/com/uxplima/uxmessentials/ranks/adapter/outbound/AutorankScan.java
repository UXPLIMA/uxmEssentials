package com.uxplima.uxmessentials.ranks.adapter.outbound;

import java.time.Duration;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.ranks.application.RanksConfig.AutorankSettings;
import com.uxplima.uxmessentials.ranks.application.Rankup;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The autorank scan: a single repeating task on the {@link Scheduler} port that, every configured interval,
 * promotes each online player who already meets their next rank's requirements: without them running
 * {@code /rankup}. It owns no promotion logic of its own; it reuses the {@link Rankup} use case per player, so the
 * requirement, cost and action pipeline is exactly the one {@code /rankup} runs. The {@code charge-cost} setting
 * is threaded straight into {@link Rankup#rankUp(PlayerRef, boolean)}, so a scan promotes for free when an operator
 * turns charging off and charges the rank's cost when they leave it on.
 *
 * <p><b>One step per player per scan.</b> Each pass advances an eligible player at most one rung. A player who
 * qualifies for several ranks at once climbs one per interval rather than all in a single sweep, bounded, simple,
 * and enough for a periodic promotion; the operator can shorten the interval if faster climbing is wanted.
 *
 * <p><b>Threading (Folia).</b> The repeating task runs on the global region thread, where the online roster is
 * coherent; {@link #tick} does nothing there but snapshot each online player to a {@link PlayerRef} and hop the
 * promotion to that player's own entity thread through {@link Scheduler#onEntity}. It never reads or mutates a
 * foreign entity inline, the {@link Rankup} pipeline (a placeholder or inventory requirement check, the action
 * runner) runs on the owning region thread, exactly as the {@code /rankup} command thread would. This mirrors the
 * {@code WorldAutoUnloadSweep} / {@code StaffFollowService} enumerate-on-global-then-hop-per-entity idiom.
 *
 * <p><b>Opt-in.</b> {@link #start()} schedules nothing and hands back a no-op closeable when autorank is disabled,
 * so a disabled scan holds zero runtime state; the wiring always calls {@code start()} and closes the returned
 * handle on module stop.
 */
@NullMarked
public final class AutorankScan {

    private final Server server;
    private final Scheduler scheduler;
    private final Rankup rankup;
    private final AutorankSettings settings;
    private final Logger log;

    public AutorankScan(Server server, Scheduler scheduler, Rankup rankup, AutorankSettings settings, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.rankup = Objects.requireNonNull(rankup, "rankup");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Start the repeating scan, returning the handle the module closes on stop to cancel it. When autorank is
     * disabled nothing is scheduled and a no-op closeable is returned, so disabling it leaves no task running.
     * Otherwise the first scan fires one interval in and then every interval.
     */
    public AutoCloseable start() {
        if (!settings.enabled()) {
            return () -> {};
        }
        Duration period = settings.interval();
        return scheduler.repeatGlobal(this::tick, period, period);
    }

    /** One scan pass on the global region thread: snapshot the online roster, hop each promotion to its owner. */
    public void tick() {
        for (Player player : server.getOnlinePlayers()) {
            PlayerRef who = BukkitRefs.toRef(player);
            scheduler.onEntity(who, () -> promote(who));
        }
    }

    // One promotion step for one player, on that player's own region thread. Guarded so a single failing attempt
    // is logged with the player's id and skipped rather than aborting the rest of the scan.
    private void promote(PlayerRef who) {
        try {
            rankup.rankUp(who, settings.chargeCost());
        } catch (RuntimeException failure) {
            log.error("autorank promotion failed for player " + who.uuid(), failure);
        }
    }
}
