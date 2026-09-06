package com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.servertweaks.domain.ChatReportPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the no-chat-reports listener over a real {@link AsyncChatEvent}. When the tweak is on and the
 * message arrived signed, the line is re-delivered to every viewer (an unsigned system message) and the event is
 * cancelled so the server's signed delivery never runs; when the tweak is off, or the message was already unsigned,
 * the listener is a strict no-op: nothing is re-sent and the event is left to flow.
 */
class UnsignedChatListenerTest {

    private static final ChatRenderer AS_IS = (source, displayName, message, viewer) -> message;

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enabledReDeliversASignedMessageUnsignedAndCancels() {
        PlayerMock speaker = server.addPlayer("Speaker");
        PlayerMock viewer = server.addPlayer("Viewer");
        Component text = Component.text("hello");
        AsyncChatEvent event = chatEvent(speaker, Set.of(viewer), text, signed(speaker.getUniqueId(), "hello"));

        new UnsignedChatListener(new ChatReportPolicy(true)).onChat(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(viewer.nextComponentMessage()).isEqualTo(text);
    }

    @Test
    void disabledIsANoOp() {
        PlayerMock speaker = server.addPlayer("Speaker");
        PlayerMock viewer = server.addPlayer("Viewer");
        AsyncChatEvent event =
                chatEvent(speaker, Set.of(viewer), Component.text("hi"), signed(speaker.getUniqueId(), "hi"));

        new UnsignedChatListener(new ChatReportPolicy(false)).onChat(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(viewer.nextComponentMessage()).isNull();
    }

    @Test
    void anAlreadyUnsignedMessageIsLeftAlone() {
        PlayerMock speaker = server.addPlayer("Speaker");
        PlayerMock viewer = server.addPlayer("Viewer");
        // A system-sourced message has the nil identity, so it already carries no signature to strip.
        SignedMessage system = SignedMessage.system("hi", Component.text("hi"));
        AsyncChatEvent event = chatEvent(speaker, Set.of(viewer), Component.text("hi"), system);

        new UnsignedChatListener(new ChatReportPolicy(true)).onChat(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(viewer.nextComponentMessage()).isNull();
    }

    private static AsyncChatEvent chatEvent(
            Player speaker, Set<Audience> viewers, Component message, SignedMessage signed) {
        return new AsyncChatEvent(true, speaker, viewers, AS_IS, message, message, signed);
    }

    /** A minimal signed message: a real player identity (so {@link SignedMessage#isSystem()} is false). */
    private static SignedMessage signed(UUID player, String text) {
        return new SignedMessage() {
            @Override
            public Instant timestamp() {
                return Instant.EPOCH;
            }

            @Override
            public long salt() {
                return 0L;
            }

            @Override
            public SignedMessage.Signature signature() {
                return SignedMessage.signature(new byte[] {1, 2, 3, 4});
            }

            @Override
            public Component unsignedContent() {
                return Component.text(text);
            }

            @Override
            public String message() {
                return text;
            }

            @Override
            public Identity identity() {
                return Identity.identity(player);
            }
        };
    }
}
