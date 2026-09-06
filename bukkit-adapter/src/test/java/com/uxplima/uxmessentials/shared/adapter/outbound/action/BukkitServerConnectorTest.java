package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Pins the proxy {@link BukkitServerConnector}: the BungeeCord channel it registers on construction, the exact
 * {@code Connect} + server frame it encodes (which Velocity honours on the same legacy name), and the degraded
 * single-server path where the channel is gone and a connect request becomes a logged no-op rather than a thrown
 * "channel not registered". The real connector had no direct test before. The action runner only ever exercised
 * a recording fake of the {@link ServerConnector} port, so the byte encoding and the availability guard went
 * unpinned until the menu engine started reaching the same connector for its {@code [connect]} action.
 */
class BukkitServerConnectorTest {

    private static final String CHANNEL = "BungeeCord";

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private RecordingLogger log;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Steve");
        log = new RecordingLogger();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registersTheBungeeChannelAndReportsAvailable() {
        ServerConnector connector = new BukkitServerConnector(plugin, log);

        assertThat(connector.isAvailable()).isTrue();
        assertThat(server.getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL))
                .isTrue();
    }

    @Test
    void encodesTheConnectSubchannelFrame() throws IOException {
        byte[] frame = BukkitServerConnector.connectFrame("lobby");

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame))) {
            assertThat(in.readUTF()).isEqualTo("Connect");
            assertThat(in.readUTF()).isEqualTo("lobby");
            assertThat(in.available()).isZero();
        }
    }

    @Test
    void connectSendsThroughTheChannelWhenAvailable() {
        ServerConnector connector = new BukkitServerConnector(plugin, log);

        connector.connect(player, "survival");

        // MockBukkit's sendPluginMessage rejects an unregistered channel, so reaching here with no warning means
        // the frame left through the registered BungeeCord channel rather than tripping the availability guard.
        assertThat(log.warnings).isEmpty();
    }

    @Test
    void connectIsALoggedNoOpWhenTheChannelIsGone() {
        ServerConnector connector = new BukkitServerConnector(plugin, log);
        server.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);

        connector.connect(player, "survival");

        assertThat(connector.isAvailable()).isFalse();
        assertThat(log.warnings).anyMatch(line -> line.contains("click_connect_skipped"));
    }

    private static final class RecordingLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
