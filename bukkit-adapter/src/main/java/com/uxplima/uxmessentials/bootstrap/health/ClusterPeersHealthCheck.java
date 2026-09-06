package com.uxplima.uxmessentials.bootstrap.health;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.ClusterPeers;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * Reports how many peer backends this one currently sees on the cross-server bus, for {@code /uxmess doctor}.
 * The figure is the live count from the {@link ClusterPeers} roster. Peers that have heartbeated within the
 * liveness window, plus how long ago the most recent peer activity was, so an operator can tell a healthy
 * multi-backend cluster ("3 peer(s) seen, last activity 4s ago") from a single standalone server ("standalone")
 * from a cluster that has gone silent (peers seen, but last activity minutes ago).
 *
 * <p>This check is registered only for an enabled backend; a backend with network sync off has no roster and
 * is standalone by definition, which the {@link BusTransportHealthCheck} line already reports. It is purely a
 * read of an in-memory roster (no blocking, no Bukkit API) so it is safe on the doctor run thread.
 */
@NullMarked
public final class ClusterPeersHealthCheck implements HealthCheck {

    private final ClusterPeers peers;
    private final LongSupplier clock;

    public ClusterPeersHealthCheck(ClusterPeers peers) {
        this(peers, System::currentTimeMillis);
    }

    ClusterPeersHealthCheck(ClusterPeers peers, LongSupplier clock) {
        this.peers = Objects.requireNonNull(peers, "peers");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String name() {
        return "cluster-peers";
    }

    @Override
    public HealthResult check() {
        long now = clock.getAsLong();
        int count = peers.livePeerCount(now);
        if (count == 0) {
            return HealthResult.ok("standalone (no peers seen)");
        }
        return HealthResult.ok(count + " peer(s) seen, last activity " + lastActivity(now));
    }

    private String lastActivity(long now) {
        Optional<Long> last = peers.lastActivity();
        if (last.isEmpty()) {
            return "never";
        }
        long agoMillis = Math.max(0, now - last.get());
        return Duration.ofMillis(agoMillis).toSeconds() + "s ago";
    }
}
