package com.uxplima.uxmessentials.presence.application;

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
 * The presence bounded context as a first-class {@link FeatureModule}: it owns the transient
 * {@code PlayerPresence} aggregate per player (AFK, plus the vanished flag it mirrors from the vanish context's
 * authority), the {@code /afk /list /nick /whois /realname /gc /staff} command surface
 * (docs/10-feature-modules.md §15.8), the dual sync-move /
 * async-chat activity listeners, and the self-rescheduling AFK idle sweep on the {@code Scheduler} port. Vanish
 * itself moved out to its own context: presence reads that state, it does not own it, and a presence reader
 * (the {@code %..._vanished%} placeholder, the sleep exclusion) sees whatever the vanish authority holds, or
 * "nobody is hidden" when that module is off.
 *
 * <p>Presence persists nothing in v1 (no DB, no migration): the per-player presence map is in-memory and
 * dropped on quit. The use cases, the in-memory store, the activity listeners, the visibility applier, and the
 * AFK sweep are constructed in the adapter wiring once the module has started; the lifecycle bookkeeping here
 * keeps {@code stop()} honest: the sweep observes the running flag and exits cleanly on disable.
 */
@NullMarked
public final class PresenceModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("presence");
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
        return PresenceCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The move/chat activity listeners and the join/quit vanish-suppression listener are Bukkit-facing and
        // land with the inbound adapter; a disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // Presence persists nothing in v1, the per-player presence map is transient in-memory state, so the
        // module owns no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases, the in-memory PresenceStore, the activity listeners, the VisibilityApplier, and the
        // self-rescheduling AFK sweep are constructed over ctx.kernel() in the adapter wiring; the lifecycle
        // bookkeeping (running flag, in-flight counter) is armed here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; the async AFK sweep observes this and exits on stop. */
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
