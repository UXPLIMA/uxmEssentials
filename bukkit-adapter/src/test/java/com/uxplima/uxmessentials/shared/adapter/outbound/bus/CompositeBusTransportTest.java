package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.network.BusTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Coverage of {@link CompositeBusTransport}, the {@code network.transport = both} fan-out, against two fake
 * {@link BusTransport}s, with no Bukkit, Redis or codec in the loop.
 *
 * <ul>
 *   <li>{@code start} wires the same {@code onFrame} sink into both members, so a frame received on either
 *       member reaches the single sink the {@code BusCore} registered;
 *   <li>{@code send} fans out to both members. A backend may reach some peers over the proxy and others over
 *       Redis, so every frame goes on both wires;
 *   <li>{@code stop} stops both members;
 *   <li>{@code healthy} is true when either member is healthy (a degraded path is still a usable bus).
 * </ul>
 */
class CompositeBusTransportTest {

    private FakeTransport velocity;
    private FakeTransport redis;
    private CompositeBusTransport composite;

    @BeforeEach
    void setUp() {
        velocity = new FakeTransport();
        redis = new FakeTransport();
        composite = new CompositeBusTransport(velocity, redis);
    }

    @Test
    void startWiresTheSameSinkIntoBothMembers() {
        List<byte[]> received = new ArrayList<>();
        composite.start(received::add);

        assertThat(velocity.started).isTrue();
        assertThat(redis.started).isTrue();
    }

    @Test
    void aFrameReceivedOnEitherMemberReachesTheSink() {
        List<byte[]> received = new ArrayList<>();
        composite.start(received::add);

        velocity.inject(new byte[] {1});
        redis.inject(new byte[] {2});

        assertThat(received).containsExactly(new byte[] {1}, new byte[] {2});
    }

    @Test
    void sendFansOutToBothMembers() {
        composite.start(frame -> {});
        byte[] frame = new byte[] {7, 7};

        composite.send(frame);

        assertThat(velocity.sent).containsExactly(frame);
        assertThat(redis.sent).containsExactly(frame);
    }

    @Test
    void stopStopsBothMembers() {
        composite.start(frame -> {});

        composite.stop();

        assertThat(velocity.stopped).isTrue();
        assertThat(redis.stopped).isTrue();
    }

    @Test
    void healthyWhenEitherMemberIsHealthy() {
        composite.start(frame -> {});

        velocity.healthy = true;
        redis.healthy = false;
        assertThat(composite.healthy())
                .as("the proxy path alone is a usable bus")
                .isTrue();

        velocity.healthy = false;
        redis.healthy = true;
        assertThat(composite.healthy())
                .as("the redis path alone is a usable bus")
                .isTrue();

        velocity.healthy = false;
        redis.healthy = false;
        assertThat(composite.healthy()).as("neither path can deliver").isFalse();
    }

    /** An in-memory {@link BusTransport}: records the calls and replays injected frames into the shared sink. */
    private static final class FakeTransport implements BusTransport {

        private final List<byte[]> sent = new ArrayList<>();
        private @org.jspecify.annotations.Nullable Consumer<byte[]> onFrame;
        private boolean started;
        private boolean stopped;
        private boolean healthy;

        @Override
        public void start(Consumer<byte[]> onFrame) {
            this.onFrame = onFrame;
            started = true;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public void send(byte[] frame) {
            sent.add(frame);
        }

        @Override
        public boolean healthy() {
            return healthy;
        }

        /** Stand in for a peer frame arriving on this member. */
        void inject(byte[] frame) {
            Consumer<byte[]> sink = onFrame;
            if (sink != null) {
                sink.accept(frame);
            }
        }
    }
}
