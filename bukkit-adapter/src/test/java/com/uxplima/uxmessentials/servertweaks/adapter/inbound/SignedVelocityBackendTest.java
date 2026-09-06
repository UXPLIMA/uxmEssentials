package com.uxplima.uxmessentials.servertweaks.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.SignedVelocityChatListener;
import com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.SignedVelocityCommandListener;
import com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.SignedVelocityQuitListener;
import com.uxplima.uxmessentials.servertweaks.application.SignedDirectiveQueue;
import com.uxplima.uxmessentials.servertweaks.domain.SignedSource;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The backend half of the SignedVelocity handshake, end to end over the shared {@link SignedDirectiveQueue}: the
 * channel listener decodes a proxy frame onto the queue (ignoring other channels and logging malformed frames), and
 * the chat and command listeners then apply the queued ruling. Cancel, modify, or leave alone when nothing is queued
 * (the no-proxy case). The quit listener forgets a player's buffered rulings.
 */
class SignedVelocityBackendTest {

    private static final ChatRenderer AS_IS = (source, displayName, message, viewer) -> message;

    private ServerMock server;
    private SignedDirectiveQueue queue;
    private RecordingLogger log;
    private SignedVelocityChannelListener channel;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        queue = new SignedDirectiveQueue();
        log = new RecordingLogger();
        channel = new SignedVelocityChannelListener(queue, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aCancelFrameCancelsTheChatEvent() {
        PlayerMock player = server.addPlayer("Steve");
        channel.onPluginMessageReceived(
                SignedVelocityChannelListener.CHANNEL,
                player,
                frame(player.getUniqueId(), "CHAT_RESULT", "CANCEL", null));

        AsyncChatEvent event = chatEvent(player, Component.text("secret"));
        new SignedVelocityChatListener(queue).onChat(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void aModifyFrameRewritesTheChatMessage() {
        PlayerMock player = server.addPlayer("Steve");
        channel.onPluginMessageReceived(
                SignedVelocityChannelListener.CHANNEL,
                player,
                frame(player.getUniqueId(), "CHAT_RESULT", "MODIFY", "cleaned"));

        AsyncChatEvent event = chatEvent(player, Component.text("original"));
        new SignedVelocityChatListener(queue).onChat(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(PlainTextComponentSerializer.plainText().serialize(event.message()))
                .isEqualTo("cleaned");
    }

    @Test
    void noQueuedRulingLeavesChatUntouched() {
        PlayerMock player = server.addPlayer("Steve");

        AsyncChatEvent event = chatEvent(player, Component.text("original"));
        new SignedVelocityChatListener(queue).onChat(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(PlainTextComponentSerializer.plainText().serialize(event.message()))
                .isEqualTo("original");
    }

    @Test
    void aModifyFrameRewritesACommand() {
        PlayerMock player = server.addPlayer("Steve");
        channel.onPluginMessageReceived(
                SignedVelocityChannelListener.CHANNEL,
                player,
                frame(player.getUniqueId(), "COMMAND_RESULT", "MODIFY", "/safe"));

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/danger");
        new SignedVelocityCommandListener(queue).onCommand(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(event.getMessage()).isEqualTo("/safe");
    }

    @Test
    void aCancelFrameCancelsACommand() {
        PlayerMock player = server.addPlayer("Steve");
        channel.onPluginMessageReceived(
                SignedVelocityChannelListener.CHANNEL,
                player,
                frame(player.getUniqueId(), "COMMAND_RESULT", "CANCEL", null));

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/danger");
        new SignedVelocityCommandListener(queue).onCommand(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void aFrameOnAnotherChannelIsIgnored() {
        PlayerMock player = server.addPlayer("Steve");
        channel.onPluginMessageReceived(
                "some:other", player, frame(player.getUniqueId(), "CHAT_RESULT", "CANCEL", null));

        assertThat(queue.poll(player.getUniqueId(), SignedSource.CHAT)).isEmpty();
    }

    @Test
    void aMalformedFrameIsLoggedAndDropped() {
        PlayerMock player = server.addPlayer("Steve");

        channel.onPluginMessageReceived(SignedVelocityChannelListener.CHANNEL, player, new byte[] {0x00, 0x01, 0x02});

        assertThat(log.warnings.get()).isEqualTo(1);
        assertThat(queue.poll(player.getUniqueId(), SignedSource.CHAT)).isEmpty();
    }

    @Test
    void quitForgetsBufferedRulings() {
        PlayerMock player = server.addPlayer("Steve");
        channel.onPluginMessageReceived(
                SignedVelocityChannelListener.CHANNEL,
                player,
                frame(player.getUniqueId(), "CHAT_RESULT", "CANCEL", null));

        new SignedVelocityQuitListener(queue)
                .onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(queue.poll(player.getUniqueId(), SignedSource.CHAT)).isEmpty();
    }

    private static AsyncChatEvent chatEvent(Player player, Component message) {
        Set<Audience> viewers = Set.of(player);
        SignedMessage system = SignedMessage.system("m", message);
        return new AsyncChatEvent(true, player, viewers, AS_IS, message, message, system);
    }

    private static byte[] frame(UUID player, String source, String result, @Nullable String modified) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(player.toString());
            out.writeUTF(source);
            out.writeUTF(result);
            if (modified != null) {
                out.writeUTF(modified);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** A {@link Logger} that only counts warnings, enough to assert a malformed frame was reported. */
    private static final class RecordingLogger implements Logger {
        private final AtomicInteger warnings = new AtomicInteger();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.incrementAndGet();
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
