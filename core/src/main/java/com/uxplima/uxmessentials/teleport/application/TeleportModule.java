package com.uxplima.uxmessentials.teleport.application;

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
 * The teleport bounded context as a first-class {@link FeatureModule}: it owns all movement
 * orchestration and the shared cooldown/warmup machinery, the tpa suite, {@code /back}, {@code /rtp},
 * spawn, the admin and positional verbs, plus the move-cancels-warmup invariant. The module declares
 * its command surface and its enable gate here; {@code start} constructs the use cases over the injected
 * kernel ports and the context's own ports, and {@code stop} drains them so a disabled module holds zero
 * runtime state.
 *
 * <p>This phase delivers the pure {@code :core} domain and application. The inbound adapters (the
 * Brigadier handlers, the move/death/expiry listeners) and the outbound adapters (the back store, the
 * pre-warmed RTP queue, the teleport executor) land in the bukkit-adapter and persistence-adapter
 * phases; until then {@link #listeners()} and {@link #migrations()} are empty by design rather than
 * half-wired, and {@link #start(ModuleContext)} only arms the lifecycle bookkeeping. The module is not
 * yet registered in {@code DefaultModuleRegistry}; it is wired in when its adapters arrive.
 */
@NullMarked
public final class TeleportModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("teleport");
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
        return TeleportCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The move-cancel, death-capture, request-expiry, and join re-arm listeners are Bukkit-facing
        // and land with the inbound adapter; a disabled or not-yet-adapted module registers none.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The pre-warmed RTP queue's durable mirror (rtp_queue table) lands with the persistence
        // adapter (ADR 0010); until then the queue is in-memory and the module owns no migrations.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // Use cases are constructed over ctx.kernel() and the context's own ports once those adapters
        // are injected through the module context in the adapter phase. The lifecycle bookkeeping
        // (running flag, in-flight counter) is armed here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; self-rescheduling work observes this and exits on stop. */
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
