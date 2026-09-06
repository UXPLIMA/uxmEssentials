package com.uxplima.uxmessentials.shared.application.health;

/**
 * The severity of a single {@link HealthCheck} outcome, ordered weakest to strongest so the aggregate worst
 * status is the {@code max} over a run. {@link #OK} is a healthy subsystem, {@link #WARN} a degraded-but-running
 * one (a configured-but-absent integration, a non-fatal mismatch), and {@link #FAIL} a broken subsystem an
 * operator must act on (the database is unreachable).
 */
public enum HealthStatus {

    /** The subsystem is healthy. */
    OK,

    /** The subsystem runs but is degraded or misconfigured: worth an operator's attention. */
    WARN,

    /** The subsystem is broken and needs operator action. */
    FAIL;

    /**
     * The more severe of {@code this} and {@code other}, used to fold a run's results into one overall status.
     * Severity follows the declaration order ({@code OK} &lt; {@code WARN} &lt; {@code FAIL}), so the comparison
     * is the natural enum order.
     */
    public HealthStatus worst(HealthStatus other) {
        return compareTo(other) >= 0 ? this : other;
    }
}
