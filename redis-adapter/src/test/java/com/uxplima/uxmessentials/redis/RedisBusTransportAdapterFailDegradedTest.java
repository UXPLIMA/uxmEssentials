package com.uxplima.uxmessentials.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.function.Consumer;

import com.uxplima.uxmlib.redis.RedisBus;
import org.junit.jupiter.api.Test;

/**
 * Fail-degraded behaviour: when Redis is unreachable a synchronous publish throw must never propagate back into
 * the bus's send path. The frame is dropped and the failure logged, so the local path keeps working while the
 * cross-server fan-out is temporarily unavailable. The lib catches an asynchronous publish-future failure; this
 * pins the adapter's own guard around a synchronous throw, with the rate-limited WARN still firing.
 */
class RedisBusTransportAdapterFailDegradedTest {

    private static final String CHANNEL = "uxmessentials:bus_v1";

    @Test
    void send_does_not_throw_when_the_channel_publish_fails() {
        ThrowingChannel channel = new ThrowingChannel();
        RecordingLogger logger = new RecordingLogger();
        RedisBusTransportAdapter transport = new RedisBusTransportAdapter(channel, CHANNEL, logger);
        transport.start(frame -> {});

        assertThatCode(() -> transport.send(new byte[] {1, 2, 3})).doesNotThrowAnyException();
        assertThat(logger.warnings()).isNotEmpty();
    }

    private static final class ThrowingChannel implements RedisBus {
        @Override
        public void publish(String channel, byte[] frame) {
            throw new IllegalStateException("redis down");
        }

        @Override
        public void subscribe(String channel, Consumer<byte[]> onFrame) {}

        @Override
        public void close() {}
    }
}
