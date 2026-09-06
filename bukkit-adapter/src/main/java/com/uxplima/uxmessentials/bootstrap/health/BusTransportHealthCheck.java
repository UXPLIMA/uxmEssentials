package com.uxplima.uxmessentials.bootstrap.health;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusHealth;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * Reports the cross-server bus's live delivery state for {@code /uxmess doctor}, deeper than the configured/not
 * listing: it surfaces whether network sync is enabled, which transport carries the bus, and, the point of the
 * check: whether that transport can actually deliver frames <em>right now</em>. The signal is the running
 * transport's own {@code healthy()} flag, read through the {@link BusHealth} view: a cheap volatile read, never a
 * blocking connect, so the check is safe on the doctor run thread.
 *
 * <p>A disabled backend ({@code network.enabled = false}, the default) reports {@code OK} as "disabled" without
 * touching the transport. An enabled backend whose transport can deliver reports {@code OK} as "connected"; one
 * whose transport currently cannot. A dropped Redis subscribe connection, a proxy bus with its channel
 * unregistered: reports {@code WARN} as "DISCONNECTED". That warning is the classic silent failure this line
 * exists to catch: the bus is configured and enabled, yet cross-server delivery is silently dead.
 */
@NullMarked
public final class BusTransportHealthCheck implements HealthCheck {

    private final BusHealth bus;

    public BusTransportHealthCheck(BusHealth bus) {
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    @Override
    public String name() {
        return "cross-server-bus";
    }

    @Override
    public HealthResult check() {
        if (!bus.enabled()) {
            return HealthResult.ok("disabled (network.enabled = false); running local-only");
        }
        String transport = "transport=" + bus.transport();
        if (bus.healthy()) {
            return HealthResult.ok("enabled, " + transport + ", connected");
        }
        return HealthResult.warn("enabled, " + transport + ", DISCONNECTED. Cross-server delivery is not working");
    }
}
