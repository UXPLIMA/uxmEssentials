package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The brand chat prefix ({@code <tag:'…'>} / {@code <etag:'…'>}) belongs to chat lines, not to inventory titles. An
 * anvil prompt renders its label through {@link GuiText#unprefixedText}, which strips that one leading token so the
 * anvil title reads as the bare prompt, no {@code uxmEssentials »} brand, while {@link GuiText#text} keeps it for
 * chat. These pin the strip rule and the rendered result.
 */
class GuiTextPrefixTest {

    private static final PlayerRef VIEWER = new PlayerRef(new java.util.UUID(0L, 0L), "tester");

    @Test
    void stripsALeadingBrandTagPrefix() {
        assertThat(GuiText.stripBrandPrefix("<tag:'WARP'> Enter the warp name:"))
                .isEqualTo("Enter the warp name:");
    }

    @Test
    void stripsALeadingErrorTagPrefix() {
        assertThat(GuiText.stripBrandPrefix("<etag:'WARP'> Prompt cancelled.")).isEqualTo("Prompt cancelled.");
    }

    @Test
    void leavesAPrefixlessLineUnchanged() {
        assertThat(GuiText.stripBrandPrefix("<body>Type a material</body>")).isEqualTo("<body>Type a material</body>");
    }

    @Test
    void unprefixedTextRendersNoBrandSeparatorInTheTitle() {
        GuiText guiText = new GuiText(prompt("<tag:'WARP'> Enter the warp name:"));

        Component anvilTitle = guiText.unprefixedText(VIEWER, DummyKey.INSTANCE, Map.of());
        Component chatLine = guiText.text(VIEWER, DummyKey.INSTANCE, Map.of());

        String anvil = PlainTextComponentSerializer.plainText().serialize(anvilTitle);
        String chat = PlainTextComponentSerializer.plainText().serialize(chatLine);
        assertThat(anvil).isEqualTo("Enter the warp name:");
        // The category prefix renders a "▶" separator; the chat line keeps it, the anvil title must not.
        assertThat(chat).contains("▶");
        assertThat(anvil).doesNotContain("▶");
    }

    private static Messages prompt(String value) {
        return (viewer, key, placeholders) -> value;
    }

    private enum DummyKey implements MessageKey {
        INSTANCE;

        @Override
        public String key() {
            return "test.prompt";
        }
    }
}
