package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMeta;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMetaSource;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatPlaceholderExpander;
import com.uxplima.uxmessentials.communication.domain.ChatFormatPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;

/**
 * MockBukkit coverage of {@link ChatFormatListener}. The {@link AsyncChatEvent} is a Mockito double (the real chat
 * pipeline is not needed) with a real {@link PlayerMock} speaker so the permission check and name/world reads run;
 * the installed {@link ChatRenderer} is captured and invoked directly so the rendered line is observable. Proves a
 * formatted line reads prefix + display name + suffix + message, that a per-group format is selected by the
 * speaker's primary group, and that a {@code <red>} tag in a player's own message stays literal unless the speaker
 * is permitted to format their message.
 */
class ChatFormatListenerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String FORMAT_PERMISSION = "uxmessentials.communication.chat.format";

    private ServerMock server;
    private PlayerMock speaker;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        speaker = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFormattedLineReadsPrefixDisplayNameSuffixAndMessage() {
        ChatFormatPolicy policy = new ChatFormatPolicy(
                true, "<prefix><display_name><suffix><gray>:</gray> <message>", Map.of(), false, "", "");
        ChatRenderer renderer = render(policy, new ChatMeta("[VIP] ", " ★", Optional.empty()));

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hello"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("[VIP] Alice ★: hello");
    }

    @Test
    void aPerGroupFormatIsSelectedByTheSpeakersPrimaryGroup() {
        ChatFormatPolicy policy = new ChatFormatPolicy(
                true, "<display_name>: <message>", Map.of("vip", "<display_name> [vip]: <message>"), false, "", "");
        ChatRenderer renderer = render(policy, new ChatMeta("", "", Optional.of("vip")));

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hello"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("Alice [vip]: hello");
    }

    @Test
    void aNonPermittedPlayersMiniMessageTagStaysLiteral() {
        ChatFormatPolicy policy = new ChatFormatPolicy(true, "<display_name>: <message>", Map.of(), true, "", "");
        ChatRenderer renderer = render(policy, ChatMeta.empty());

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hi <red>there"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("Alice: hi <red>there");
    }

    @Test
    void aPermittedPlayersMiniMessageTagIsParsed() {
        speaker.addAttachment(MockBukkit.createMockPlugin(), FORMAT_PERMISSION, true);
        ChatFormatPolicy policy = new ChatFormatPolicy(true, "<display_name>: <message>", Map.of(), true, "", "");
        ChatRenderer renderer = render(policy, ChatMeta.empty());

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hi <red>there"), speaker);

        String plain = PLAIN.serialize(line);
        assertThat(plain).isEqualTo("Alice: hi there");
        assertThat(plain).doesNotContain("<red>");
    }

    @Test
    void aDisabledPolicyInstallsNoRenderer() {
        ChatFormatPolicy policy = ChatFormatPolicy.disabled();
        ChatFormatListener listener =
                new ChatFormatListener(() -> policy, source(ChatMeta.empty()), ChatPlaceholderExpander.identity());
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(speaker);

        listener.onChat(event);

        verify(event, never()).renderer(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aLegacyColourCodedPrefixRendersInColourNotLiterally() {
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "<prefix><display_name><gray>:</gray> <message>", Map.of(), false, "", "");
        ChatRenderer renderer = render(policy, new ChatMeta("&c[VIP] ", "", Optional.empty()));

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hello"), speaker);

        // The &c code is consumed (no literal "&c") and re-emitted as a red span, not shown verbatim.
        assertThat(PLAIN.serialize(line)).isEqualTo("[VIP] Alice: hello");
        assertThat(MiniMessage.miniMessage().serialize(line)).contains("red");
    }

    @Test
    void aSectionSignLegacyPrefixRendersInColourToo() {
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "<prefix><display_name><gray>:</gray> <message>", Map.of(), false, "", "");
        ChatRenderer renderer = render(policy, new ChatMeta("\u00A7c[VIP] ", "", Optional.empty()));

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hello"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("[VIP] Alice: hello");
        assertThat(MiniMessage.miniMessage().serialize(line)).contains("red");
    }

    @Test
    void aMiniMessagePrefixIsStillParsedAsMiniMessage() {
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "<prefix><display_name><gray>:</gray> <message>", Map.of(), false, "", "");
        ChatRenderer renderer = render(policy, new ChatMeta("<red>[VIP] ", "", Optional.empty()));

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hello"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("[VIP] Alice: hello");
        assertThat(MiniMessage.miniMessage().serialize(line)).contains("red");
    }

    @Test
    void aPapiPlaceholderInTheFormatIsExpandedWhenTheSeamIsPresent() {
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "%papi_tag% <display_name>: <message>", Map.of(), false, "", "");
        ChatPlaceholderExpander present = (who, text) -> text.replace("%papi_tag%", "[STAFF]");
        ChatRenderer renderer = render(policy, ChatMeta.empty(), present);

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hi"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("[STAFF] Alice: hi");
    }

    @Test
    void aPapiPlaceholderIsLeftUntouchedWhenTheSeamIsAbsent() {
        ChatFormatPolicy policy =
                new ChatFormatPolicy(true, "%papi_tag% <display_name>: <message>", Map.of(), false, "", "");
        ChatRenderer renderer = render(policy, ChatMeta.empty()); // identity expander, no PlaceholderAPI

        Component line = renderer.render(speaker, Component.text("Alice"), Component.text("hi"), speaker);

        assertThat(PLAIN.serialize(line)).isEqualTo("%papi_tag% Alice: hi");
    }

    /** Run the listener with {@code policy} + {@code meta} and hand back the renderer it installed on the event. */
    private ChatRenderer render(ChatFormatPolicy policy, ChatMeta meta) {
        return render(policy, meta, ChatPlaceholderExpander.identity());
    }

    /** Run the listener with {@code policy} + {@code meta} + a specific PlaceholderAPI seam, returning the renderer. */
    private ChatRenderer render(ChatFormatPolicy policy, ChatMeta meta, ChatPlaceholderExpander expander) {
        ChatFormatListener listener = new ChatFormatListener(() -> policy, source(meta), expander);
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(speaker);

        listener.onChat(event);

        ArgumentCaptor<ChatRenderer> captor = ArgumentCaptor.forClass(ChatRenderer.class);
        verify(event).renderer(captor.capture());
        return captor.getValue();
    }

    private static ChatMetaSource source(ChatMeta meta) {
        return new ChatMetaSource() {
            @Override
            public ChatMeta metaFor(UUID who) {
                return meta;
            }
        };
    }
}
