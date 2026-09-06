package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import com.uxplima.uxmessentials.shared.network.BusTransport;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Transport parity for the plugin-messaging path: every one of the thirteen {@link NetworkMessage} frame types
 * survives the {@link PluginMessagingTransport} seam byte-identically. The transport carries opaque
 * {@code byte[]}, so parity means the bytes {@link BusCore} hands the transport on a publish equal
 * {@link NetworkMessageCodec#encode}, and the exact same bytes fed back through the transport's inbound path
 * decode to a frame equal to the original.
 *
 * <p>The companion sweep over the other transport lives in {@code RedisBusTransportAdapterParityTest} in the
 * {@code :redis-adapter} module (where the Redis adapter and its fake-channel seam are reachable). Both sweeps
 * walk an identical one-representative-of-every-wire-type list and both guard that the list size equals
 * {@link NetworkMessage.MessageType#values()} length, so the set of frames proven over this transport, the set
 * proven over Redis, and the full set of wire types are one and the same. The codec's own round-trip across all
 * thirteen is covered in {@code NetworkMessageCodecTest}.
 */
class BusTransportParityTest {

    private static final String SELF = "survival-1";
    private static final String PEER = "lobby-2";

    private ServerMock server;
    private Plugin plugin;
    private InlineScheduler scheduler;
    private RecordingListener listener;
    private RemoteSyncRegistry registry;
    private RecordingTransport recorder;
    private PluginMessagingTransport pluginMessaging;
    private BusCore core;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        scheduler = new InlineScheduler();
        listener = new RecordingListener();
        registry = new RemoteSyncRegistry();
        registry.register(listener);
        pluginMessaging = new PluginMessagingTransport(plugin, scheduler, BusChannel.FULL, 256);
        // Wrap the real transport so the bytes the core hands it on a publish are captured verbatim; the wrapper
        // delegates start/send/stop to the real transport so the actual plugin-messaging machinery still runs.
        recorder = new RecordingTransport(pluginMessaging);
        core = new BusCore(recorder, SELF, registry, new SilentLogger());
        core.start();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    static Stream<NetworkMessage> everyFrame() {
        return NetworkFrames.oneOfEach(PEER).stream();
    }

    @ParameterizedTest
    @MethodSource("everyFrame")
    void publishHandsTheTransportTheExactCodecBytes(NetworkMessage frame) {
        core.publish(frame);

        assertThat(recorder.sent).containsExactly(NetworkMessageCodec.encode(frame));
    }

    @ParameterizedTest
    @MethodSource("everyFrame")
    void anInboundFrameDecodesBackEqualToTheOriginal(NetworkMessage frame) {
        // The exact codec bytes arrive on the channel and travel the real transport's inbound path back to the
        // core, which decodes them and dispatches the frame to the listener. Proving the transport carried the
        // bytes verbatim.
        pluginMessaging.onPluginMessageReceived(BusChannel.FULL, carrier(), NetworkMessageCodec.encode(frame));

        assertThat(listener.applied).containsExactly(frame);
    }

    @Test
    void theSweepCoversEveryWireType() {
        assertThat(NetworkFrames.oneOfEach(PEER))
                .hasSize(NetworkMessage.MessageType.values().length)
                .extracting(NetworkMessage::type)
                .containsExactlyInAnyOrder(NetworkMessage.MessageType.values());
    }

    private Player carrier() {
        return server.addPlayer();
    }

    /** Delegates to a real {@link BusTransport} while recording the bytes sent through it. */
    private static final class RecordingTransport implements BusTransport {

        private final BusTransport delegate;
        private final List<byte[]> sent = new ArrayList<>();

        RecordingTransport(BusTransport delegate) {
            this.delegate = delegate;
        }

        @Override
        public void start(Consumer<byte[]> onFrame) {
            delegate.start(onFrame);
        }

        @Override
        public void stop() {
            delegate.stop();
        }

        @Override
        public void send(byte[] frame) {
            sent.add(frame);
            delegate.send(frame);
        }

        @Override
        public boolean healthy() {
            return delegate.healthy();
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

    /** Runs every scheduled task inline so the flush and inbound hand-off fire in the test thread. */
    private static final class InlineScheduler implements Scheduler {

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** A logger that discards every line; the parity decisions are asserted, not the log output. */
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
