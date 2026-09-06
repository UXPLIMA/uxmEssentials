package com.uxplima.uxmessentials.shared.application.health;

import java.util.Objects;

/**
 * The outcome of one {@link HealthCheck}: a {@link HealthStatus} severity plus a short human message an operator
 * reads on the {@code /uxmess doctor} surface. A value type, platform-neutral by design: the bukkit-side checks
 * produce it from probes against persistence, the {@code ServicesManager}, and the soft-depend plugins, but it
 * names none of them.
 *
 * @param status the severity of this outcome
 * @param message a one-line operator-facing description (the version it found, why it warned, what failed)
 */
public record HealthResult(HealthStatus status, String message) {

    public HealthResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");
    }

    /** A healthy result carrying {@code message}. */
    public static HealthResult ok(String message) {
        return new HealthResult(HealthStatus.OK, message);
    }

    /** A degraded result carrying {@code message}. */
    public static HealthResult warn(String message) {
        return new HealthResult(HealthStatus.WARN, message);
    }

    /** A broken result carrying {@code message}. */
    public static HealthResult fail(String message) {
        return new HealthResult(HealthStatus.FAIL, message);
    }
}
