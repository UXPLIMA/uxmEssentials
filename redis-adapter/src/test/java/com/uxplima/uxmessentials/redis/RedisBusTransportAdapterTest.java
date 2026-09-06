package com.uxplima.uxmessentials.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.network.BalanceChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import com.uxplima.uxmlib.redis.RedisBus;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RedisBusTransportAdapter} without a live Redis: a {@link CapturingChannel} stands in for
 * the underlying {@link RedisBus}, grabbing the inbound {@code Consumer<byte[]>} the adapter registers so frames
 * can be fed straight through the delivery path, and recording every published frame. The self-origin echo drop
 * is {@code BusCore}'s concern above this seam, not the transport's, so these tests pin only the
 * {@code BusTransport} lifecycle the adapter owns.
 */
class RedisBusTransportAdapterTest {

    private static final String CHANNEL = "uxmessentials:bus_v1";

    @Test
    void aReceivedFrameIsHandedToOnFrameVerbatim() {
        CapturingChannel channel = new CapturingChannel();
        RedisBusTransportAdapter transport = new RedisBusTransportAdapter(channel, CHANNEL, new RecordingLogger());
        List<byte[]> received = new ArrayList<>();
        transport.start(received::add);

        byte[] frame = NetworkMessageCodec.encode(new BalanceChanged("peer-2", UUID.randomUUID(), "coins"));
        channel.feed(frame);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isEqualTo(frame);
        assertThat(NetworkMessageCodec.decode(received.get(0))).isInstanceOf(BalanceChanged.class);
    }

    @Test
    void sendPublishesTheExactFrameBytesOnTheConfiguredChannel() {
        CapturingChannel channel = new CapturingChannel();
        RedisBusTransportAdapter transport = new RedisBusTransportAdapter(channel, CHANNEL, new RecordingLogger());
        transport.start(frame -> {});

        byte[] frame = NetworkMessageCodec.encode(new BalanceChanged("self-1", UUID.randomUUID(), "coins"));
        transport.send(frame);

        assertThat(channel.published()).hasSize(1);
        assertThat(channel.published().get(0).channel()).isEqualTo(CHANNEL);
        assertThat(channel.published().get(0).frame()).isEqualTo(frame);
    }

    @Test
    void sendDefensivelyCopiesTheFrame() {
        CapturingChannel channel = new CapturingChannel();
        RedisBusTransportAdapter transport = new RedisBusTransportAdapter(channel, CHANNEL, new RecordingLogger());
        transport.start(frame -> {});

        byte[] frame = {1, 2, 3};
        transport.send(frame);
        frame[0] = 99; // mutate after send. The published copy must be unaffected

        assertThat(channel.published().get(0).frame()).isEqualTo(new byte[] {1, 2, 3});
    }

    @Test
    void sendBeforeStartNeverTouchesRedis() {
        CapturingChannel channel = new CapturingChannel();
        RedisBusTransportAdapter transport = new RedisBusTransportAdapter(channel, CHANNEL, new RecordingLogger());

        transport.send(new byte[] {1, 2, 3});

        assertThat(channel.published()).isEmpty();
    }

    @Test
    void startIsANoOpWhenAlreadyRunning() {
        CapturingChannel channel = new CapturingChannel();
        RedisBusTransportAdapter transport = new RedisBusTransportAdapter(channel, CHANNEL, new RecordingLogger());

        transport.start(frame -> {});
        transport.start(frame -> {});

        assertThat(channel.subscribeCount()).hasValue(1);
    }

    @Test
    void healthyReflectsTheChannelStateAndStop() {
        CapturingChannel channel = new CapturingChannel();
        RedisBusTransportAdapter transport = new RedisBusTransportAdapter(channel, CHANNEL, new RecordingLogger());

        assertThat(transport.healthy()).isFalse();

        transport.start(frame -> {});
        assertThat(transport.healthy()).isTrue();

        channel.setHealthy(false);
        assertThat(transport.healthy()).isFalse();

        channel.setHealthy(true);
        transport.stop();
        assertThat(transport.healthy()).isFalse();
    }

    @Test
    void stopIsIdempotentAndClosesTheChannel() {
        CapturingChannel channel = new CapturingChannel();
        RedisBusTransportAdapter transport = new RedisBusTransportAdapter(channel, CHANNEL, new RecordingLogger());
        transport.start(frame -> {});

        transport.stop();
        transport.stop();

        assertThat(channel.closeCount()).hasValue(2);
        assertThat(transport.healthy()).isFalse();
    }

    /** A stand-in for the Redis wire: captures the inbound consumer and every published channel/frame pair. */
    private static final class CapturingChannel implements RedisBus {

        private @Nullable Consumer<byte[]> onFrame;
        private final AtomicInteger subscribeCount = new AtomicInteger();
        private final AtomicInteger closeCount = new AtomicInteger();
        private final List<Published> published = new ArrayList<>();
        private volatile boolean healthy = true;

        @Override
        public void publish(String channel, byte[] frame) {
            published.add(new Published(channel, frame));
        }

        @Override
        public void subscribe(String channel, Consumer<byte[]> onFrame) {
            this.onFrame = onFrame;
            subscribeCount.incrementAndGet();
        }

        @Override
        public boolean healthy() {
            return healthy;
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
        }

        void feed(byte[] frame) {
            Consumer<byte[]> sink = onFrame;
            if (sink == null) {
                throw new IllegalStateException("no active subscriber to feed");
            }
            sink.accept(frame);
        }

        void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        AtomicInteger subscribeCount() {
            return subscribeCount;
        }

        AtomicInteger closeCount() {
            return closeCount;
        }

        List<Published> published() {
            return published;
        }
    }

    /** A captured publish; a plain holder because Error Prone forbids array record components. */
    private static final class Published {
        private final String channel;
        private final byte[] frame;

        Published(String channel, byte[] frame) {
            this.channel = channel;
            this.frame = frame;
        }

        String channel() {
            return channel;
        }

        byte[] frame() {
            return frame;
        }
    }
}
