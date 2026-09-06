package com.uxplima.uxmessentials.npc.application;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The npc bounded context as a first-class {@link FeatureModule}: it owns the {@code Npc} aggregate (a
 * server-wide named, world-placed fake player with an optional skin and an optional click command) and the
 * single {@code /npc} command serving the create / delete / list / move / skin / command forms. NPCs are
 * DB-persisted so they survive a restart; the fake players are rendered to each viewer entirely with packets
 * (no real entity) on join and a refresh tick, and despawned on disable, through the uxmLib NPC packet API.
 *
 * <p>The {@code npc} table ships in the persistence baseline (V38 under {@code db/migration}, always applied by
 * the persistence layer), so the module declares no extra migration location of its own; a disabled module
 * leaves the baseline table in place but wires nothing over it. The use cases, the jOOQ repository over
 * {@code persistence.dsl()}, and the packet-backed renderer are constructed in the adapter wiring once the
 * module has started; the lifecycle bookkeeping here keeps {@code stop()} honest. On disable the spawned fake
 * players are removed from every viewer. The module ships ENABLED but inert: with no NPCs created it renders
 * nothing and costs only the empty refresh iteration.
 */
@NullMarked
public final class NpcModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("npc");
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile boolean running;

    @Override
    public ModuleId id() {
        return ID;
    }

    @Override
    public String configRoot() {
        return ID.configRoot();
    }

    @Override
    public List<CommandSpec> commands() {
        return NpcCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The join/quit/world-change lifecycle listener is Bukkit-facing and is installed by the inbound adapter
        // wiring; a disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The npc table is part of the persistence baseline (db/migration V38), always applied by the
        // persistence layer, so the module owns no additional Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases, the jOOQ repository and the packet renderer are constructed over ctx.kernel() and the
        // persistence DSL in the adapter wiring; the lifecycle bookkeeping is armed here so stop() is already
        // honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; async spawn-on-enable work observes this and exits on stop. */
    public boolean isRunning() {
        return running;
    }

    private void awaitDrain() {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }
}
