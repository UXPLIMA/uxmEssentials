package com.uxplima.uxmessentials.shared.application.module;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The injection envelope handed to a module's {@link FeatureModule#start(ModuleContext)}.
 *
 * <p>A module never reaches for the plugin instance or news up a port. It receives what it needs
 * here, applying constructor injection at the module seam. The envelope carries the module's own
 * {@link ModuleId}, its configuration subtree (scoped by convention to the module's
 * {@code configRoot}), and the {@link KernelPorts} bundle of shared outbound ports (scheduler,
 * permissions, cooldowns, warmups, messages, lookups, the in-process event bus). Per-context
 * persistence ports are still threaded through each bounded context's own wiring as it lands.
 *
 * @param moduleId identity of the module being started
 * @param config the configuration store, scoped by convention to the module's {@code configRoot}
 * @param kernel the shared cross-cutting outbound ports
 */
public record ModuleContext(ModuleId moduleId, ConfigStore config, KernelPorts kernel) {

    public ModuleContext {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(kernel, "kernel");
    }
}
