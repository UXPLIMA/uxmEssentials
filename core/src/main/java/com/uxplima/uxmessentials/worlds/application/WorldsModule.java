package com.uxplima.uxmessentials.worlds.application;

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
 * The worlds bounded context as a first-class {@link FeatureModule}: it owns the world aggregate and
 * the {@code /worlds} management surface, and delegates teleport execution (sub-project D) to the
 * teleport context, so it is registered after teleport in {@code DefaultModuleRegistry}. The world
 * tables ship in the persistence baseline (V61), so the module declares no migration of its own; the
 * bukkit-side adapters (Brigadier handlers, the {@code BukkitWorldEngine}, the jOOQ repository) are
 * constructed in {@code WorldsWiring} once the module has started.
 */
@NullMarked
public final class WorldsModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("worlds");
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
        return WorldCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while started; async world operations observe this and exit on stop. */
    public boolean isRunning() {
        return running;
    }

    /** Increment before scheduling async work, decrement on completion, so {@link #stop()} can drain. */
    public AtomicInteger inFlight() {
        return inFlight;
    }

    private void awaitDrain() {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }
}
