package com.uxplima.uxmessentials.kits.application;

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
 * The kits bounded context as a first-class {@link FeatureModule}: it owns the kit definitions (config-driven,
 * one file per kit under {@code modules/kits/kits/}), the claim rules (per-kit permission, the shared cooldown
 * gate, the persisted one-time stamp, and an optional cost), and the kit claim/list/authoring/reset commands.
 * The per-kit cost
 * soft-couples to the economy context: kits charge only when an economy provider is present, so the module
 * is registered after economy but never hard depends on it.
 *
 * <p>The module needs no database. Kit definitions live one file per kit under {@code modules/kits/kits/}, and
 * per-player claim/cooldown state is transient PDC, so the module declares no migration location: a disabled
 * module wires nothing and
 * leaves no schema behind. The module declares its command surface and enable gate here; {@code start} arms
 * the lifecycle bookkeeping, and the bukkit-side adapters (the Brigadier handlers, the config-backed
 * repository, and the PDC claim store) are constructed in the adapter wiring once the module has started.
 */
@NullMarked
public final class KitsModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("kits");
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
        return KitCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The read-only /kit show preview menu's click/drag guard is Bukkit-facing and is registered through the
        // adapter wiring (KitsWiring) rather than this kernel-side factory list; a disabled module registers none.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // Kits need no database: definitions live one file per kit under modules/kits/kits/ and
        // claim/cooldown state is transient PDC, so the module owns no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases, the config-backed repository, and the PDC claim store are constructed over
        // ctx.kernel() in the adapter wiring; the lifecycle bookkeeping is armed here so stop() is honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started. */
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
