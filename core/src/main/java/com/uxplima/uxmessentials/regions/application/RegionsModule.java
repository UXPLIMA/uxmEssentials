package com.uxplima.uxmessentials.regions.application;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The regions bounded context as a first-class {@link FeatureModule}: a GUI to manage WorldGuard regions (Phase 1
 * lists them; the flag and members/owners editors land with the later phases). WorldGuard is a soft dependency
 * the adapter wiring probes for it and, when it is absent, binds a no-op {@code RegionService} so the module is
 * inert (the {@code /regions} command reports "WorldGuard not installed" and opens nothing).
 *
 * <p><b>Ships disabled by default.</b> It is a GUI over WorldGuard's regions, so there is nothing to show on a
 * server without WorldGuard and {@link #enabled(ConfigStore)} defaults to {@code false}; an operator running
 * WorldGuard flips {@code modules.regions.enabled = true}. It persists nothing of its own (WorldGuard owns the
 * region store), so the module declares no Flyway location.
 *
 * <p>The {@code /regions} command is Bukkit-facing and, together with the {@code RegionService} implementation
 * selection, lands in the adapter wiring once the module has started; the lifecycle bookkeeping here keeps
 * {@link #stop()} honest. A disabled module wires zero commands, zero listeners and zero state (the
 * {@code FeatureModule} contract).
 */
@NullMarked
public final class RegionsModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("regions");

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
        // The /regions command is Bukkit-facing and bound only when WorldGuard is present, so it is contributed
        // through the adapter wiring rather than this declarative list; a disabled module publishes none.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The region list GUI is opened on demand from the command; the context registers no listener.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // WorldGuard owns the region store; the regions context persists nothing, so it owns no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships DISABLED: it is a GUI over WorldGuard's regions, so it has nothing to show on a server
        // without WorldGuard installed.
        return config.getBoolean(configRoot() + ".enabled", false);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The RegionService implementation (real over WorldGuard, or the no-op fallback) and the /regions command
        // are constructed over ctx in the adapter wiring; the running flag is armed here so stop() is already
        // honest before any surface lands.
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started. */
    public boolean isRunning() {
        return running;
    }
}
