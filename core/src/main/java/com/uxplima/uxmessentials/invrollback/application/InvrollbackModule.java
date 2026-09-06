package com.uxplima.uxmessentials.invrollback.application;

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
 * The invrollback bounded context as a first-class {@link FeatureModule}: AxInventoryRestore-parity inventory
 * snapshots captured at key moments (death, logout) so staff can restore a lost inventory from a GUI. The
 * snapshots are DB-backed and survive a world rollback (the same hard invariant the economy and vaults ledgers
 * hold), never PDC.
 *
 * <p><b>Ships disabled by default.</b> Every death and logout writes a snapshot row, a cost worth paying only on
 * a server whose staff actually restore inventories, so {@link #enabled(ConfigStore)} defaults to {@code false};
 * an operator flips {@code modules.invrollback.enabled = true} to start capturing.
 *
 * <p>The {@code inv_snapshots} table ships in the persistence baseline ({@code db/migration}, always applied by
 * the persistence layer), so the module declares no extra migration location of its own, the same arrangement
 * vaults/holograms use. The jOOQ {@code SnapshotRepository} and the death/logout capture listener are constructed
 * in the adapter wiring once the module has started; the lifecycle bookkeeping here keeps {@code stop()} honest.
 * The {@code /invrestore} GUI command is Bukkit-facing and lands with a later phase, so this phase publishes no
 * command.
 */
@NullMarked
public final class InvrollbackModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("invrollback");

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
        // The /invrestore staff command is Bukkit-facing and lands with the inbound adapter in Phase 2; this
        // phase publishes none.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The death/logout capture listeners are Bukkit-facing and land with the inbound adapter wiring; a
        // disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The inv_snapshots table is part of the persistence baseline (db/migration), always applied by the
        // persistence layer, so the module owns no additional Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships DISABLED: every death and logout writes an inventory snapshot to the database, a cost
        // worth paying only on a server whose staff actually restore inventories.
        return config.getBoolean(configRoot() + ".enabled", false);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The jOOQ SnapshotRepository, the CaptureSnapshot use case and the capture listener are constructed over
        // ctx.kernel() and the persistence DSL in the adapter wiring; the running flag is armed here so stop() is
        // already honest before any capture lands.
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started; the async captures observe this and exit on stop. */
    public boolean isRunning() {
        return running;
    }
}
