package com.uxplima.uxmessentials.vanish.application;

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
 * The vanish bounded context as a first-class {@link FeatureModule}: the single authority over vanish state and its
 * visibility. It owns {@code /vanish} and the transient vanish store every other context reads (messaging's {@code
 * /msg} resolution, the nametag viewer cull, staff-mode vanish), so there is exactly one vanish state in the plugin.
 *
 * <p><b>Ships enabled by default.</b> The bundled config turns vanish on out of the box, so {@link #enabled(ConfigStore)}
 * defaults to {@code true} like the steady-state contexts; an operator flips {@code modules.vanish.enabled = false} to
 * turn it off, at which point the messaging/nametags vanish gates degrade to "no one is hidden" and staff-mode vanish
 * becomes a no-op. It persists nothing (the vanish state is transient in-memory state re-derived on join) so the
 * module owns no Flyway location.
 *
 * <p>The {@code /vanish} command, the packet-hide view, and the join/quit listener are Bukkit-facing and land with the
 * adapter wiring; a disabled module wires zero of them (the {@code FeatureModule} contract).
 */
@NullMarked
public final class VanishModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("vanish");

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
        // /vanish is Bukkit-facing and registered by the adapter wiring as a CommandRegistration; no CommandSpec here.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The join/quit listener lands with the inbound adapter; a disabled module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // vanish persists nothing: the vanish state is transient in-memory state re-derived on join.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The store, view, use case, command, and listener are constructed over ctx in the adapter wiring; the
        // lifecycle bookkeeping (running flag) is armed here so stop() is already honest before any behaviour lands.
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started; the adapter's background work observes this and exits on stop. */
    public boolean isRunning() {
        return running;
    }
}
