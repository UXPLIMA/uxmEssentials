package com.uxplima.uxmessentials.bootstrap.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusHealth;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.health.HealthStatus;
import org.junit.jupiter.api.Test;

/**
 * Pins the cross-server bus line {@code /uxmess doctor} renders from the live {@link BusHealth} view. The four
 * states an operator needs to tell apart are covered: the bus disabled (the default), enabled over the proxy,
 * enabled over a connected Redis, and enabled over a Redis whose subscribe connection has dropped: the last is
 * the silent-failure case the line exists to surface. The health signal is the transport's own
 * {@link BusHealth#healthy()} flag, read here through a fake so no real transport is opened.
 */
class BusTransportHealthCheckTest {

    @Test
    void disabledBusReportsOkAndNeverReadsTheTransport() {
        BusTransportHealthCheck check = new BusTransportHealthCheck(fake(false, "velocity", false));

        HealthResult result = check.check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.message()).contains("disabled");
    }

    @Test
    void enabledVelocityBusReportsConnectedWhenHealthy() {
        BusTransportHealthCheck check = new BusTransportHealthCheck(fake(true, "velocity", true));

        HealthResult result = check.check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.message()).contains("velocity").contains("connected");
    }

    @Test
    void enabledRedisBusReportsConnectedWhenHealthy() {
        BusTransportHealthCheck check = new BusTransportHealthCheck(fake(true, "redis", true));

        HealthResult result = check.check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.message()).contains("redis").contains("connected");
    }

    @Test
    void enabledButDisconnectedBusWarnsWithTheTransportName() {
        BusTransportHealthCheck check = new BusTransportHealthCheck(fake(true, "redis", false));

        HealthResult result = check.check();

        assertThat(result.status()).isEqualTo(HealthStatus.WARN);
        assertThat(result.message()).contains("redis").contains("DISCONNECTED");
    }

    @Test
    void readsTheHealthyFlagLiveOnEachCheck() {
        // The flag flips between checks (a transport that connects, then drops): the check must read it each time,
        // never cache a snapshot taken at construction.
        MutableBusHealth health = new MutableBusHealth(true, "redis", false);
        BusTransportHealthCheck check = new BusTransportHealthCheck(health);

        assertThat(check.check().status()).isEqualTo(HealthStatus.WARN);
        health.healthy = true;
        assertThat(check.check().status()).isEqualTo(HealthStatus.OK);
    }

    @Test
    void namesTheBusSubsystem() {
        assertThat(new BusTransportHealthCheck(fake(false, "velocity", false)).name())
                .isEqualTo("cross-server-bus");
    }

    private static BusHealth fake(boolean enabled, String transport, boolean healthy) {
        return new MutableBusHealth(enabled, transport, healthy);
    }

    /** A hand-rolled {@link BusHealth} whose {@code healthy} flag a test can flip between checks. */
    private static final class MutableBusHealth implements BusHealth {
        private final boolean enabled;
        private final String transport;
        private boolean healthy;

        MutableBusHealth(boolean enabled, String transport, boolean healthy) {
            this.enabled = enabled;
            this.transport = transport;
            this.healthy = healthy;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public String transport() {
            return transport;
        }

        @Override
        public boolean healthy() {
            return healthy;
        }
    }
}
