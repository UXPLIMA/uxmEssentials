package com.uxplima.uxmessentials.holograms.application;

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
 * The holograms bounded context as a first-class {@link FeatureModule}: it owns the {@code Hologram}
 * aggregate (a server-wide named, world-placed, multi-line text display) and the single {@code /hologram}
 * command serving the create / delete / list / line-edit / move forms. Holograms are DB-persisted so they
 * survive a restart; the live entities are native {@code TextDisplay}s spawned on enable and despawned on
 * disable through the uxmLib hologram API.
 *
 * <p>The {@code holograms} and {@code hologram_lines} tables ship in the persistence baseline (V13 under
 * {@code db/migration}, always applied by the persistence layer), so the module declares no extra migration
 * location of its own; a disabled module leaves the baseline tables in place but wires nothing over them. The
 * use cases, the jOOQ repository over {@code persistence.dsl()}, and the uxmLib-backed renderer are
 * constructed in the adapter wiring once the module has started; the lifecycle bookkeeping here keeps
 * {@code stop()} honest: on disable the spawned holograms are despawned.
 */
@NullMarked
public final class HologramsModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("holograms");
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
        return HologramCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The uxmLib hologram lifecycle listener is Bukkit-facing and is installed by the inbound adapter
        // wiring; a disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The holograms / hologram_lines tables are part of the persistence baseline (db/migration V13),
        // always applied by the persistence layer, so the module owns no additional Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases, the jOOQ repository and the uxmLib renderer are constructed over ctx.kernel() and
        // the persistence DSL in the adapter wiring; the lifecycle bookkeeping is armed here so stop() is
        // already honest.
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
