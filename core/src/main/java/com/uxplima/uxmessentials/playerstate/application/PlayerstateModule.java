package com.uxplima.uxmessentials.playerstate.application;

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
 * The playerstate bounded context as a first-class {@link FeatureModule}: it owns the per-player toggle/effect
 * surface. {@code /god /fly /heal /feed /gamemode /speed} plus the {@code /ext /suicide /near /nightvision
 * /flyspeed /walkspeed /ptime /pweather} verbs and the {@code /exp /air /burn /ice /getpos /ping /rest} utility
 * verbs. Over an immutable {@code PlayerStateSnapshot} reconciled with the live player on its owning region
 * thread. The module declares its command surface and enable gate here;
 * {@code start} arms the lifecycle bookkeeping, and the bukkit-side adapters (the Brigadier handlers, the
 * in-memory snapshot store, the reconciler, the effects bridge, and the join/quit/respawn listener) are
 * constructed in the adapter wiring once the module has started.
 *
 * <p>The module needs no database: the snapshot map is transient in-memory state held only while a player is
 * online (no persistence, no PDC), so the module declares no migration location. A disabled module wires
 * nothing and leaves no schema or state behind. {@code /repair}, {@code /repairall}, {@code /hat}, and
 * {@code /more} are owned by the itemworld context and are deliberately not part of this surface.
 */
@NullMarked
public final class PlayerstateModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("playerstate");
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
        return PlayerstateCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The join/quit/respawn re-apply/reset listener is Bukkit-facing and is registered through the
        // adapter wiring (PlayerstateWiring) rather than this kernel-side factory list; a disabled module
        // registers none.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // Playerstate persists nothing: the snapshot map is transient in-memory state, so the module owns
        // no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases, the in-memory snapshot store, the reconciler, and the effects bridge are
        // constructed over ctx.kernel() in the adapter wiring; the lifecycle bookkeeping is armed here so
        // stop() is honest.
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
