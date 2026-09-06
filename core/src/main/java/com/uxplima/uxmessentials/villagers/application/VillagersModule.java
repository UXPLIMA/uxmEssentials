package com.uxplima.uxmessentials.villagers.application;

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
 * The villagers bounded context as a first-class {@link FeatureModule}: villager trade management (Phase 1
 * infinite trading, a configurable restock timer, instant restock, and a global/per-villager trade toggle). The
 * behaviour is almost entirely Bukkit villager and merchant-recipe handling, so the trade and interact listeners
 * and the restock sweep land with the adapter wiring; this module stands up the identity, the enable gate, and the
 * lifecycle bookkeeping behind them.
 *
 * <p><b>Ships disabled by default.</b> Turning it on changes what a villager trades and how often it restocks,
 * an economy decision no fresh install should make for the operator.
 *
 * <p><b>Ships enabled but inert.</b> {@link #enabled(ConfigStore)} defaults to {@code true} like the steady-state
 * contexts, but every sub-feature ships {@code false} in the bundled config, so a fresh install changes no villager
 * behaviour until an operator turns a feature on. It persists nothing, the per-villager last-restock stamp and the
 * disable flag are PDC state on the villager entity itself, so the module owns no Flyway location.
 *
 * <p>Phase 1 publishes no declarative command and registers no declarative listener: the listeners and the sweep are
 * contributed through the adapter wiring, gated per sub-feature, and a disabled module wires zero of them (the
 * {@code FeatureModule} contract). The wiring drains the sweep and drops its state on {@link #stop()} so a disable or
 * reload strands no scheduled work.
 */
@NullMarked
public final class VillagersModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("villagers");

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
        // Phase 1 is entity-mechanics only; the trade-manager /villager command lands with Phase 2's adapter wiring.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The trade / interact listeners and the restock sweep are contributed through the adapter wiring, gated per
        // sub-feature; a disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // villagers persists nothing: the last-restock stamp and disable flag live in the villager's own PDC, so the
        // module owns no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships DISABLED: turning it on changes what a villager will trade and how often it restocks,
        // which is an economy decision no fresh install should make for the operator.
        return config.getBoolean(configRoot() + ".enabled", false);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The typed config, PDC flag store, listeners, and restock sweep are constructed over ctx in the adapter
        // wiring; the lifecycle bookkeeping (running flag) is armed here so stop() is already honest before any
        // behaviour lands.
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started; the sweep observes this and exits on stop. */
    public boolean isRunning() {
        return running;
    }
}
