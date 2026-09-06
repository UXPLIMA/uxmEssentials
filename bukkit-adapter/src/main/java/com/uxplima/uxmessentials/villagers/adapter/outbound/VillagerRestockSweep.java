package com.uxplima.uxmessentials.villagers.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.villagers.domain.RestockPolicy;
import org.jspecify.annotations.NullMarked;

/**
 * The restock timer: a single repeating task on the {@link Scheduler} port that, every configured interval, restocks
 * the trades of every loaded villager whose last restock is older than the interval, replacing the vanilla
 * work-station cycle with a predictable sweep. It owns no timing rule of its own; the {@link RestockPolicy} decides
 * whether a villager is due from its PDC last-restock stamp, and {@link VillagerTrades#restockAll} performs the reset.
 *
 * <p><b>Threading (Folia).</b> A world's villagers span every region, and each is owned by the region thread its
 * location falls in. So {@link #tick} runs on the global region thread. The one thread that can read a whole world's
 * roster coherently. Where it snapshots each villager's owning-region {@link Position} and hops the restock onto that
 * region via {@link Scheduler#onRegion}. It never reads or mutates a foreign villager inline; the due-check, the
 * recipe reset, and the re-stamp all run on the villager's own region thread. This mirrors the
 * {@code BukkitEntityPurger} enumerate-on-global-then-hop-per-region idiom. On Paper every region is the main thread,
 * so the sweep behaves exactly like a straight inline pass.
 *
 * <p><b>Opt-in.</b> {@link #start()} schedules nothing and hands back a no-op closeable when the restock timer is
 * disabled, so a disabled sweep holds zero runtime state; the wiring always calls {@code start()} and closes the
 * returned handle on module stop.
 */
@NullMarked
public final class VillagerRestockSweep {

    private final Server server;
    private final Scheduler scheduler;
    private final PdcVillagerFlags flags;
    private final RestockPolicy policy;
    private final Duration interval;
    private final Clock clock;
    private final boolean enabled;

    public VillagerRestockSweep(
            Server server,
            Scheduler scheduler,
            PdcVillagerFlags flags,
            RestockPolicy policy,
            Duration interval,
            Clock clock,
            boolean enabled) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.flags = Objects.requireNonNull(flags, "flags");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.interval = Objects.requireNonNull(interval, "interval");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.enabled = enabled;
    }

    /**
     * Start the repeating sweep, returning the handle the module closes on stop to cancel it. When the restock timer
     * is disabled nothing is scheduled and a no-op closeable is returned, so disabling it leaves no task running.
     * Otherwise the first sweep fires one interval in and then every interval.
     */
    public AutoCloseable start() {
        if (!enabled) {
            return () -> {};
        }
        return scheduler.repeatGlobal(this::tick, interval, interval);
    }

    /** One sweep pass on the global region thread: snapshot each world's villagers, hop each restock to its region. */
    public void tick() {
        Instant now = clock.instant();
        for (World world : server.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Villager villager) {
                    Position position = BukkitRefs.toPosition(villager.getLocation());
                    scheduler.onRegion(position, () -> restockIfDue(villager, now));
                }
            }
        }
    }

    // On the villager's own region thread: restock its trades and re-stamp when the interval has elapsed. The validity
    // check guards against a villager that despawned between the global snapshot and this hop.
    private void restockIfDue(Villager villager, Instant now) {
        if (!villager.isValid()) {
            return;
        }
        if (policy.restockDue(flags.lastRestock(villager), now)) {
            VillagerTrades.restockAll(villager);
            flags.stampRestock(villager, now);
        }
    }
}
