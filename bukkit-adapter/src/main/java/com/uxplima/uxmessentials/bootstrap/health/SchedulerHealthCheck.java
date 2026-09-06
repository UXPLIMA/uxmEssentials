package com.uxplima.uxmessentials.bootstrap.health;

import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * An informational line for {@code /uxmess doctor}: whether the server is Folia (regionised threading) or
 * regular Paper. The plugin is Folia-ready either way. Every scheduled task already routes through the
 * region-aware {@code Scheduler} port, so this is always {@code OK}; it just tells the operator which threading
 * model is live, which colours how they read the rest of the report. Folia is detected by the presence of the
 * {@code RegionizedServer} class, which exists only on a Folia server.
 */
@NullMarked
public final class SchedulerHealthCheck implements HealthCheck {

    private static final String FOLIA_MARKER_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";

    @Override
    public String name() {
        return "scheduler";
    }

    @Override
    public HealthResult check() {
        return HealthResult.ok(isFolia() ? "Folia (regionised threading) detected" : "regular Paper threading");
    }

    private static boolean isFolia() {
        try {
            Class.forName(FOLIA_MARKER_CLASS);
            return true;
        } catch (ClassNotFoundException notFolia) {
            return false;
        }
    }
}
