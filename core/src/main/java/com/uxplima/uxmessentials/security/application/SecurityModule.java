package com.uxplima.uxmessentials.security.application;

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
 * The security bounded context as a first-class {@link FeatureModule}: account-security features that are not a
 * login system, and on an offline-mode server wait for the one installed rather than replacing it. Phase 1 stands up
 * the module identity, the enable gate, and the
 * two-factor enrolment surface. The {@code /2fa} and {@code /pin} verbs backed by the DB-backed
 * {@code security_2fa} store, with the PIN hashed and the TOTP secret encrypted at rest. The join-verification
 * flow, op-command protection, and IP/alt guard land as the later phases do, contributed through the adapter wiring.
 *
 * <p><b>Ships enabled by default.</b> The module is inert until a player enrols a factor, so like the steady-state
 * contexts {@link #enabled(ConfigStore)} defaults to {@code true}; an operator flips {@code modules.security.enabled
 * = false} to turn it off.
 *
 * <p>The {@code security_2fa} table ships in the persistence baseline (a {@code V##} migration under
 * {@code db/migration}, always applied by the persistence layer), so the module declares no extra Flyway location of
 * its own. Phase 1 publishes no {@link CommandSpec} and registers no listener here: the {@code /2fa} and {@code /pin}
 * commands are Bukkit-facing Brigadier registrations contributed by the adapter wiring, and a disabled module wires
 * zero either way (the {@code FeatureModule} contract). The lifecycle bookkeeping here keeps {@link #stop()} honest.
 */
@NullMarked
public final class SecurityModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("security");

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
        // The /2fa and /pin verbs are Bukkit-facing Brigadier registrations contributed by the inbound adapter;
        // the module declares no declarative CommandSpec.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The join-verification listeners (join/quit edges, the freeze move-cancel, the keypad click handler) are
        // Bukkit-facing registrations contributed by the inbound adapter wiring, like the /2fa and /pin commands;
        // the module declares no declarative ListenerFactory.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The security_2fa and security_trusted tables are part of the persistence baseline (db/migration V76,
        // V77), as is the shared ip_history table the alt lookups read (V83), always applied by the persistence
        // layer, so the module owns no additional Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships enabled but inert (nothing happens until a player enrols a factor); an operator opts
        // out via modules.conf.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The jOOQ TwoFactorRepository, the typed SecurityConfig and the enrolment use cases are constructed over
        // ctx and the persistence DSL in the adapter wiring; the running flag is armed here so stop() is honest.
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
