package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.network.BusTransport;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code network.transport = both} fan-out: a {@link BusTransport} that holds the proxy plugin-messaging
 * transport and the Redis pub/sub transport and drives both as one. A backend on a mixed network: some peers
 * reachable over the Velocity proxy, others only over a shared Redis, replicates every frame to all of them by
 * putting each outbound frame on both wires and accepting inbound frames from either.
 *
 * <p>Both members are started with the <em>same</em> {@code onFrame} sink the {@link BusCore} registers, so a
 * frame arriving on either carrier is decoded and dispatched once by the core; the core's self-origin loop
 * sentinel ({@code BusCore#onFrame}) drops this backend's own frame echoed back, and the same sentinel also
 * collapses the case where a single peer is reachable over both carriers and a frame arrives twice, the second
 * copy is a duplicate of an already-applied remote change and a cache invalidation is idempotent, so a
 * re-delivered frame is harmless. {@link #send} fans out to both members; {@link #stop} stops both.
 *
 * <h2>Health rule</h2>
 * {@link #healthy} is true when <em>either</em> member is healthy: with the proxy up but Redis down (or the
 * reverse) the bus can still reach the peers on the live carrier, so it is a usable, if degraded, bus rather
 * than a dead one. It reports unhealthy only when neither carrier can currently deliver. A transport that has
 * no path to peers simply never delivers frames ({@code BusTransport} degradation contract), so this rule never
 * gates the single-server happy path.
 *
 * <h2>Concurrency</h2>
 * Stateless beyond its two final members; every call delegates straight to them. The members own their own
 * threading (the proxy transport hops to the tick thread through the {@code Scheduler}; the Redis transport
 * runs its subscribe loop on the async executor), so this wrapper adds no lock and no shared mutable state.
 */
@NullMarked
public final class CompositeBusTransport implements BusTransport {

    private final BusTransport velocity;
    private final BusTransport redis;

    public CompositeBusTransport(BusTransport velocity, BusTransport redis) {
        this.velocity = Objects.requireNonNull(velocity, "velocity");
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    /** Start both members with the shared {@code onFrame} sink so a frame from either reaches the core once. */
    @Override
    public void start(Consumer<byte[]> onFrame) {
        Objects.requireNonNull(onFrame, "onFrame");
        velocity.start(onFrame);
        redis.start(onFrame);
    }

    /** Stop both members. Idempotent: each member's stop is itself idempotent. */
    @Override
    public void stop() {
        velocity.stop();
        redis.stop();
    }

    /** Fan one already-encoded frame out to both carriers; either may reach peers the other cannot. */
    @Override
    public void send(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        velocity.send(frame);
        redis.send(frame);
    }

    /** True when either carrier can currently deliver: a degraded single-carrier bus is still usable. */
    @Override
    public boolean healthy() {
        return velocity.healthy() || redis.healthy();
    }
}
