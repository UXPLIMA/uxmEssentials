package com.uxplima.uxmessentials.economy.application;

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
 * The economy bounded context as a first-class {@link FeatureModule}. The plugin's canonical worked DDD
 * example. It owns the {@code Wallet} aggregate, the multi-currency {@code Money}/{@code Currency} value
 * objects, and the {@code /balance} {@code /pay} {@code /baltop} {@code /paytoggle} {@code /eco} command
 * surface, all reached through the {@code EconomyProvider} port. The module is ON by default; at
 * {@code start} the bukkit-side adapter registers the native provider through the {@code ServicesManager}
 * <em>unless</em> a foreign economy already holds the registration, in which case it defers and logs
 * (register-or-defer, ADR-0004). When the module is disabled in {@code modules.conf}, nothing registers as
 * the provider and dependent features degrade gracefully: a {@code WarpCost} resolves to "free".
 *
 * <p>The economy tables ({@code economy_owners}, {@code wallet_balances}, {@code transactions}) ship in the
 * persistence baseline ({@code db/migration} V2), which the persistence layer always applies, so the module
 * declares no extra migration location of its own; a disabled module leaves the baseline tables in place but
 * wires nothing over them. The use cases, the native provider, the debounced wallet writer, and the jOOQ
 * repository are constructed over {@code ctx.kernel()} and the persistence DSL in the adapter wiring once the
 * module has started; the lifecycle bookkeeping here keeps {@code stop()} honest.
 */
@NullMarked
public final class EconomyModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("economy");
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
        return EconomyCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // A join-time wallet warm-up / starting-balance listener is Bukkit-facing and lands with the inbound
        // adapter; a disabled or not-yet-adapted module registers none.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The economy tables are part of the persistence baseline (db/migration V2), always applied by the
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
        // The use cases, the native provider + register-or-defer, the debounced wallet writer, and the jOOQ
        // repository are constructed over ctx.kernel() and the persistence DSL in the adapter wiring; the
        // lifecycle bookkeeping (running flag, in-flight counter) is armed here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; async wallet loads and the debounced writer observe this on stop. */
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
