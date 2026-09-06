package com.uxplima.uxmessentials.shared.application.health;

/**
 * One runtime diagnostic probe behind {@code /uxmess doctor}. Each check names a subsystem and reports a
 * {@link HealthResult} ({@code OK}/{@code WARN}/{@code FAIL} plus a short message) so an operator sees a
 * misconfiguration (a dead database, an economy provider lost to another plugin, a configured-but-absent
 * soft-depend) at a glance, deeper than the enable/disable listing {@code /uxmess status} gives.
 *
 * <p>This is a deliberately central seam rather than a hook on the {@code FeatureModule} contract: a method
 * there would ripple across every module and its registry guard. Instead the bootstrap assembles a
 * {@code List<HealthCheck>} from the subsystems that are actually present and hands it to the command.
 *
 * <p><b>Resilience invariant.</b> A check probes live infrastructure that can fail, so {@link #check()} must
 * convert its own failure into a {@code WARN}/{@code FAIL} result rather than propagate. A thrown check would
 * abort the whole {@code doctor} run. Run a check through {@link #safe()} to enforce that at the boundary.
 */
public interface HealthCheck {

    /** A short, stable label for the subsystem this check probes (e.g. {@code "database"}). */
    String name();

    /** Probe the subsystem and report its health. Must not throw, convert failure into a result. */
    HealthResult check();

    /**
     * A view of this check whose {@link #check()} can never throw: any exception the probe leaks is caught and
     * reported as a {@code FAIL} carrying the exception's message, so one misbehaving check never aborts the run.
     * Idempotent: wrapping a check that already honours the invariant changes nothing observable.
     */
    default HealthCheck safe() {
        HealthCheck delegate = this;
        return new HealthCheck() {
            @Override
            public String name() {
                return delegate.name();
            }

            @Override
            public HealthResult check() {
                try {
                    return delegate.check();
                } catch (RuntimeException probeFailure) {
                    String detail = probeFailure.getMessage();
                    return HealthResult.fail("check errored: " + (detail == null ? probeFailure : detail));
                }
            }
        };
    }
}
