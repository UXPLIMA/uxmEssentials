package com.uxplima.uxmessentials.trade.application;

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
 * The trade bounded context as a first-class {@link FeatureModule}: player-to-player trading ({@code /trade}) with a
 * live dual-inventory window and, in the later phases, money, a request/accept flow, and cross-server escrow. This
 * Phase-1 skeleton stands up the module identity, the enable gate, and the lifecycle bookkeeping so the pure
 * {@code TradeSession} state machine and the typed {@link TradeConfig} can be wired behind it.
 *
 * <p><b>Ships enabled by default</b> like the other steady-state contexts, so {@link #enabled(ConfigStore)} defaults
 * to {@code true}; an operator flips {@code modules.trade.enabled = false} to turn the feature off. Same-server trades
 * hold their session purely in memory ({@code TradeSession}), so the module owns no Flyway location in Phase 1, the
 * cross-server escrow table lands with the cross-server phase.
 *
 * <p>Phase 1 publishes no command and registers no listener: the {@code /trade} verbs and the window listeners arrive
 * with the inbound adapter in the behaviour phases, and a disabled module wires zero either way (the
 * {@code FeatureModule} contract). The adapter clears its registry on {@link #stop()} so a disable or reload leaves no
 * residual runtime state.
 */
@NullMarked
public final class TradeModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("trade");

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
        // /trade and its subcommands are Bukkit-facing and land with the inbound adapter in the later phases; Phase 1
        // publishes none.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The trade-window inventory listeners (place/take/close/quit) land with the inbound adapter; a disabled or
        // not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // A same-server trade is transient in-memory state held in a TradeSession, so Phase 1 owns no Flyway location.
        // The cross-server escrow table lands with the cross-server phase.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships enabled: a fresh install offers /trade out of the box, like the steady-state contexts. An
        // operator opts out via modules.conf.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The typed config and the session registry are constructed over ctx in the adapter wiring; the lifecycle
        // bookkeeping (running flag) is armed here so stop() is already honest before any behaviour lands.
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started; the later phases' background work observes this and exits on stop. */
    public boolean isRunning() {
        return running;
    }
}
