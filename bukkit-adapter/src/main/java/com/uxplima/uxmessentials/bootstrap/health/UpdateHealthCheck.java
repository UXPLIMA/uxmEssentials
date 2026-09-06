package com.uxplima.uxmessentials.bootstrap.health;

import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * An informational line for {@code /uxmess doctor} about the opt-in update checker (built on uxmLib's update
 * toolkit, wired in {@code IntegrationsWiring}). The checker announces a newer release to the console on its own
 * cadence; {@code doctor} does not run a network call, so this line only reflects whether the checker is enabled.
 * Always {@code OK}: a disabled checker is a deliberate operator choice, not a fault.
 */
@NullMarked
public final class UpdateHealthCheck implements HealthCheck {

    private final boolean updateCheckEnabled;

    public UpdateHealthCheck(boolean updateCheckEnabled) {
        this.updateCheckEnabled = updateCheckEnabled;
    }

    @Override
    public String name() {
        return "update-check";
    }

    @Override
    public HealthResult check() {
        return updateCheckEnabled
                ? HealthResult.ok("update checker enabled. Newer releases are announced to the console")
                : HealthResult.ok("update checker disabled in config");
    }
}
