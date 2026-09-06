package com.uxplima.uxmessentials.bootstrap.health;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * A summary line for {@code /uxmess doctor}: how many of the registered feature modules are enabled. It reads the
 * same {@link ModuleRegistry} and {@link ConfigStore} the {@code /uxmess status} listing does, so the counts
 * stay in lockstep with that surface. Always {@code OK}: the count is informational, not a fault condition.
 */
@NullMarked
public final class ModuleCountHealthCheck implements HealthCheck {

    private final ModuleRegistry registry;
    private final ConfigStore config;

    public ModuleCountHealthCheck(ModuleRegistry registry, ConfigStore config) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public String name() {
        return "modules";
    }

    @Override
    public HealthResult check() {
        int total = registry.all().size();
        int enabled = registry.enabledModules(config).size();
        return HealthResult.ok(enabled + " of " + total + " feature modules enabled");
    }
}
