package com.uxplima.uxmessentials.shared.application.module;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The authoritative list of every {@link FeatureModule}, consulted by the bootstrap.
 *
 * <p>There is exactly one registration site, so the registry is the single answer to "which contexts
 * exist?" Registration is explicit (no classpath scanning, no reflection, no service loader) so the
 * set stays greppable and the drift guard can compare it against the package tree and {@code
 * modules.conf}.
 *
 * <p>Order is part of the contract: {@link #all()} is dependency-first (prerequisites before
 * dependents), and teardown on disable walks it in reverse so dependents stop before prerequisites.
 */
public interface ModuleRegistry {

    /**
     * Registers a module, returning this registry so registrations chain. Implementations reject a
     * duplicate {@link ModuleId} so the set is unambiguous.
     */
    ModuleRegistry register(FeatureModule module);

    /** Every registered module, in deterministic dependency-first wiring order. */
    List<FeatureModule> all();

    /** Looks up one module by its id, for {@code /uxmess reload <id>} and {@code /uxmess doctor}. */
    Optional<FeatureModule> byId(ModuleId id);

    /**
     * The subset of {@link #all()} whose {@link FeatureModule#enabled(ConfigStore)} gate is open for
     * the given configuration, preserving wiring order. This is the list the gated wiring loop and
     * the command/listener registration loops consume; a disabled module never appears here, which is
     * what makes "disabled means absent" hold at the registration boundary.
     */
    List<FeatureModule> enabledModules(ConfigStore config);
}
