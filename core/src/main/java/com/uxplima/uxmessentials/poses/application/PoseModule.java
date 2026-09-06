package com.uxplima.uxmessentials.poses.application;

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
 * The poses bounded context as a first-class {@link FeatureModule}: GSit-parity sitting and posing ({@code /sit},
 * player-sit stacking, {@code /lay} / {@code /bellyflop} / {@code /spin}, {@code /crawl}) built in, with no separate
 * plugin. The command surface, the interact/quit listeners, and the seat entities are Bukkit-facing and land with
 * the adapter wiring in the later phases; this Phase-0 skeleton stands up the module identity, the enable gate, and
 * the lifecycle bookkeeping so the state and settings can be wired behind it.
 *
 * <p><b>Ships enabled by default.</b> The bundled config enables the common poses out of the box (player-sit is the
 * one opt-in verb), so {@link #enabled(ConfigStore)} defaults to {@code true} like the steady-state contexts; an
 * operator flips {@code modules.poses.enabled = false} to turn the feature off. It persists nothing: a pose is
 * transient in-memory state held in {@code PoseSessions}, so the module owns no Flyway location.
 *
 * <p>Phase 0 publishes no command and registers no listener: the verbs arrive as their behaviour phases land, and a
 * disabled module wires zero either way (the {@code FeatureModule} contract). The registry is cleared on
 * {@link #stop()} in the adapter wiring so a disable or reload leaves no residual runtime state.
 */
@NullMarked
public final class PoseModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("poses");

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
        // The pose verbs (/sit, /lay, /bellyflop, /spin, /crawl, /poses) are Bukkit-facing and land with the inbound
        // adapter in the later phases; Phase 0 publishes none.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The right-click-seat, right-click-player, and cancel-trigger listeners land with the inbound adapter; a
        // disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // poses persists nothing: a pose is transient in-memory state held in PoseSessions, so the module owns no
        // Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships enabled: the bundled config turns on the common poses (player-sit is opt-in), so the
        // default surface is the sit/pose verbs available to players. The default here is true (the steady-state
        // contexts default on); an operator opts out via modules.conf.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The session registry and the typed config are constructed over ctx in the adapter wiring; the lifecycle
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
