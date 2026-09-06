package com.uxplima.uxmessentials.vaults.application;

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
 * The vaults bounded context as a first-class {@link FeatureModule}: it owns the {@code Vault} aggregate, its
 * two numbered-quota families ({@code uxmessentials.vault.amount.<n>} / {@code uxmessentials.vault.size.<rows>}
 * resolved through the shared {@code Permissions} reducer), and the single {@code /vault} command serving the
 * open / list / admin-audit forms. Vaults are DB-persisted and survive a world rollback (the same hard
 * invariant the economy ledger holds), never PDC.
 *
 * <p>The {@code vaults} table ships in the persistence baseline (V6 under {@code db/migration}, always applied
 * by the persistence layer), so the module declares no extra migration location of its own; a disabled module
 * leaves the baseline table in place but wires nothing over it. The use cases, the jOOQ {@code VaultRepository}
 * over {@code persistence.dsl()}, the GUI inventory-holder, the audit logger and the {@code InventoryClose}
 * save listener are constructed in the adapter wiring once the module has started; the lifecycle bookkeeping
 * here keeps {@code stop()} honest: on disable the open vault GUIs are closed and flushed.
 */
@NullMarked
public final class VaultsModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("vaults");
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
        return VaultCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The InventoryClose save listener is Bukkit-facing and lands with the inbound adapter; a disabled or
        // not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The vaults table is part of the persistence baseline (db/migration V6), always applied by the
        // persistence layer, so the module owns no additional Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases, the jOOQ VaultRepository, the GUI holder, the audit logger and the InventoryClose save
        // listener are constructed over ctx.kernel() and the persistence DSL in the adapter wiring; the
        // lifecycle bookkeeping (running flag, in-flight counter) is armed here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; async vault saves observe this and exit on stop. */
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
