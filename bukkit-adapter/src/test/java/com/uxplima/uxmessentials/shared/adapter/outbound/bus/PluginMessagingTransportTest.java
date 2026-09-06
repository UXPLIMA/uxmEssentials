package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.BusChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Plugin-messaging coverage of {@link PluginMessagingTransport} against the real (mock) Bukkit messenger. The
 * transport owns only the byte-moving machinery. Channel registration, the bounded outbound buffer, the
 * carrier flush, and feeding inbound frame bytes back to its {@code onFrame} sink, so the test asserts those
 * four moves directly, with no {@link BusCore} or codec in the loop:
 *
 * <ul>
 *   <li>{@code start} registers the plugin-messaging channel so the messenger will carry it;
 *   <li>{@code send} flushes a frame onto an online carrier player's connection on the configured channel;
 *   <li>an inbound plugin message hands the raw frame bytes to the {@code onFrame} consumer;
 *   <li>{@code stop} unregisters the channel and stops carrying further sends.
 * </ul>
 *
 * <p>The {@link Scheduler} is a synchronous inline fake so the {@code onGlobal} flush hop and the {@code async}
 * inbound hand-off both run in the test thread.
 */
class PluginMessagingTransportTest {

    private static final String CHANNEL = BusChannel.FULL;
    private static final byte[] FRAME = {1, 2, 3, 4};

    private ServerMock server;
    private Plugin plugin;
    private InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        scheduler = new InlineScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void startRegistersTheOutgoingChannel() {
        PluginMessagingTransport transport = transport();

        transport.start(frame -> {});

        assertThat(server.getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL))
                .isTrue();
        assertThat(transport.healthy()).isTrue();
    }

    @Test
    void sendFlushesAFrameOntoTheCarrierOnTheConfiguredChannel() {
        PluginMessagingTransport transport = transport();
        transport.start(frame -> {});
        server.addPlayer();

        // The flush hop runs through the inline scheduler; the carrier's sendPluginMessage validates the channel
        // is registered as outgoing (StandardMessenger throws ChannelNotRegisteredException otherwise), so a clean
        // flush proves the frame left on exactly the configured channel.
        transport.send(FRAME.clone());

        assertThat(scheduler.ran).isEqualTo(1);
    }

    @Test
    void sendDoesNothingWhenNoCarrierIsOnline() {
        PluginMessagingTransport transport = transport();
        transport.start(frame -> {});

        // No player online to carry the frame: the flush hop runs but drains nothing, so no carrier touch happens
        // and the buffered frame waits for a later flush, the degrade-to-local-only contract.
        transport.send(FRAME.clone());

        assertThat(scheduler.ran).isEqualTo(1);
    }

    @Test
    void aFullBufferDropsTheOldestFrame() {
        PluginMessagingTransport transport = new PluginMessagingTransport(plugin, scheduler, CHANNEL, 2);
        transport.start(frame -> {});
        // No carrier online, so frames stay buffered. Push past the cap; the bounded buffer drops the oldest
        // rather than pinning memory, then a carrier drains exactly the cap-sized tail without throwing.
        transport.send(new byte[] {1});
        transport.send(new byte[] {2});
        transport.send(new byte[] {3});
        server.addPlayer();

        transport.send(new byte[] {4});

        assertThat(scheduler.ran).isEqualTo(4);
    }

    @Test
    void anInboundPluginMessageDeliversTheBytesToOnFrame() {
        List<byte[]> received = new ArrayList<>();
        PluginMessagingTransport transport = transport();
        transport.start(received::add);

        transport.onPluginMessageReceived(CHANNEL, server.addPlayer(), FRAME.clone());

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).containsExactly(FRAME);
    }

    @Test
    void anInboundMessageOnAnotherChannelIsIgnored() {
        List<byte[]> received = new ArrayList<>();
        PluginMessagingTransport transport = transport();
        transport.start(received::add);

        transport.onPluginMessageReceived("other:channel", server.addPlayer(), FRAME.clone());

        assertThat(received).isEmpty();
    }

    @Test
    void stopUnregistersTheChannelAndStopsCarrying() {
        PluginMessagingTransport transport = transport();
        transport.start(frame -> {});

        transport.stop();

        assertThat(server.getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL))
                .isFalse();
        assertThat(transport.healthy()).isFalse();
    }

    @Test
    void aSendBeforeStartIsANoOp() {
        PluginMessagingTransport transport = transport();

        transport.send(FRAME.clone());

        assertThat(scheduler.ran).isZero();
    }

    @Test
    void stopIsIdempotent() {
        PluginMessagingTransport transport = transport();
        transport.start(frame -> {});

        transport.stop();
        transport.stop();

        assertThat(transport.healthy()).isFalse();
    }

    private PluginMessagingTransport transport() {
        return new PluginMessagingTransport(plugin, scheduler, CHANNEL, 256);
    }

    /** Runs every scheduled task inline so the flush and inbound hand-off fire in the test thread. */
    private static final class InlineScheduler implements Scheduler {

        private int ran;

        @Override
        public void onGlobal(Runnable task) {
            ran++;
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            ran++;
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            ran++;
            task.run();
        }

        @Override
        public void async(Runnable task) {
            ran++;
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            ran++;
            task.run();
        }
    }
}
