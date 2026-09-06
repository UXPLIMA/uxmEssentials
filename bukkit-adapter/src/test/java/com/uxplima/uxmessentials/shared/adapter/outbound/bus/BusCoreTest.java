package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Transport-agnostic coverage of {@link BusCore} against a fake in-memory {@link BusTransport}, with no Bukkit
 * in the loop at all. The fake records the bytes the core hands to {@link BusTransport#send} and lets the test
 * inject received bytes through the {@code onFrame} sink the core registers on {@link BusTransport#start}, so
 * the three decisions the core owns are asserted directly:
 *
 * <ul>
 *   <li>a {@link #publish} encodes the message and the transport receives exactly the codec bytes;
 *   <li>an injected frame from a peer reaches every registered {@link RemoteSyncListener};
 *   <li>an injected frame whose origin is this backend's own id is dropped, the loop sentinel, and never
 *       reaches a listener.
 * </ul>
 */
class BusCoreTest {

    private static final String SELF = "survival-1";
    private static final String PEER = "lobby-2";
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private FakeTransport transport;
    private RecordingListener listener;
    private RemoteSyncRegistry registry;
    private BusCore core;

    @BeforeEach
    void setUp() {
        transport = new FakeTransport();
        listener = new RecordingListener();
        registry = new RemoteSyncRegistry();
        registry.register(listener);
        core = new BusCore(transport, SELF, registry, new SilentLogger());
        core.start();
    }

    @Test
    void publishHandsTheEncodedBytesToTheTransport() {
        HomeChanged message = new HomeChanged(SELF, OWNER);

        core.publish(message);

        assertThat(transport.sent).containsExactly(NetworkMessageCodec.encode(message));
    }

    @Test
    void aPeerFrameReachesEveryRegisteredListener() {
        HomeChanged peerChange = new HomeChanged(PEER, OWNER);

        transport.inject(NetworkMessageCodec.encode(peerChange));

        assertThat(listener.applied).containsExactly(peerChange);
    }

    @Test
    void aSelfOriginFrameIsDropped() {
        transport.inject(NetworkMessageCodec.encode(new HomeChanged(SELF, OWNER)));

        assertThat(listener.applied).isEmpty();
    }

    @Test
    void aMalformedFrameIsDroppedWithoutReachingAListener() {
        transport.inject(new byte[] {9, 9, 9});

        assertThat(listener.applied).isEmpty();
    }

    @Test
    void serverIdIsTheConfiguredOrigin() {
        assertThat(core.serverId()).isEqualTo(SELF);
    }

    @Test
    void stopReleasesTheTransport() {
        core.stop();

        assertThat(transport.stopped).isTrue();
    }

    /** An in-memory transport: records sent frames and replays injected ones into the core's sink. */
    private static final class FakeTransport implements BusTransport {

        private final List<byte[]> sent = new ArrayList<>();
        private @org.jspecify.annotations.Nullable Consumer<byte[]> onFrame;
        private boolean stopped;

        @Override
        public void start(Consumer<byte[]> onFrame) {
            this.onFrame = onFrame;
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
            return onFrame != null && !stopped;
        }

        /** Stand in for a peer frame arriving over the wire. */
        void inject(byte[] frame) {
            Consumer<byte[]> sink = onFrame;
            if (sink != null) {
                sink.accept(frame);
            }
        }
    }

    /** Records every frame the core delivered, the stand-in for a context's cache-invalidation listener. */
    private static final class RecordingListener implements RemoteSyncListener {

        private final List<NetworkMessage> applied = new ArrayList<>();

        @Override
        public void onRemoteChange(NetworkMessage message) {
            applied.add(message);
        }
    }

    /** A logger that discards every line; the dispatch decisions are asserted, not the log output. */
    private static final class SilentLogger implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
