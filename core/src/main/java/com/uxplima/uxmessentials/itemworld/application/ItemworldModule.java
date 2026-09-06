package com.uxplima.uxmessentials.itemworld.application;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The itemworld bounded context as a first-class {@link FeatureModule}: the largest module, covering the full
 * item/world command surface (~40 verbs) split into independently disableable sub-feature groups
 * (docs/10-feature-modules.md §15.10). It owns the {@code /give /i /item /itemname /itemlore /itemflag /skull
 * /more /repair /repairall /enchant /hat /itemdb} item utils, the virtual workstations, the {@code
 * /disposal /condense} cleanup, the {@code /powertool /powertooltoggle} powertool, the {@code /spawnmob
 * /spawner /kill /butcher /killall /remove /unlimited} mob/entity verbs, the {@code /time /weather} verbs with
 * their {@code /sun /rain /thunder /day /night} aliases, and the {@code /lightning /fireball /kittycannon}
 * admin-fun verbs.
 *
 * <p>itemworld is overwhelmingly stateless, ACL-thin mutations with no DB migration and no persistence: the
 * surface validates inputs at the adapter boundary and applies them through the live item/entity/world. So
 * the module declares no migration location and the only runtime state is the transient powertool bindings the
 * adapter holds as PDC stamps; the lifecycle bookkeeping here keeps {@code stop()} honest. The {@code /repair
 * /repairall /hat /more} verbs overlap playerstate by name but are owned here: playerstate deferred them
 * (§15.6), so they register here and the two modules never double-register.
 *
 * <p>On top of the module-level enable switch, each {@link SubFeatureGroup sub-feature group} is independently
 * disableable and every command carries a per-command disable, resolved from {@code itemworld.conf} through
 * {@link ItemworldConfig}. The use cases, the audit logger, the workstation openers and the powertool listener
 * are constructed over {@code ctx.kernel()} in the adapter wiring once the module has started.
 */
@NullMarked
public final class ItemworldModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("itemworld");
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
        return ItemworldCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The powertool interact listener and the inventory-close write-back for the cleanup GUIs are
        // Bukkit-facing and land with the inbound adapter; a disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // itemworld is stateless and ACL-thin: no persistence, no DB migration. Powertool bindings are transient
        // PDC stamps the adapter owns, never a table.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The validating use cases (give/enchant/spawnmob/purge/time/weather/...), the ItemworldAudit logger, the
        // virtual-workstation openers and the powertool listener are constructed over ctx.kernel() and the
        // ItemworldConfig view of ctx.config() in the adapter wiring; the lifecycle bookkeeping (running flag,
        // in-flight counter) is armed here so stop() is honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; region-touching adapter tasks observe this and exit on stop. */
    public boolean isRunning() {
        return running;
    }

    /** Resolve a command's owning sub-feature group, for the per-group + per-command disable gate at the boundary. */
    public Optional<SubFeatureGroup> groupOf(String literal) {
        return ItemworldCommandSurface.groupOf(literal);
    }

    private void awaitDrain() {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }
}
