package com.uxplima.uxmessentials.communication.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import org.junit.jupiter.api.Test;

/**
 * Proves the shipped communication defaults, the {@code /info} pages and the advancement broadcast template, are
 * authored in the canon palette and that those tokens actually resolve through the same MiniMessage path production
 * uses. The info pages render through {@code BukkitMessageSink.deliver}, which deserializes with
 * {@link StyleTags#resolver()}; the advancement template renders through {@code HudText.render}, whose second step is
 * the same {@code MiniMessage.deserialize(source, StyleTags.resolver())} (its PlaceholderAPI pre-parse is the identity
 * for a template with no {@code %papi%} token). Both are exercised here with that resolver so a non-resolving token
 * which has no locale-parity guard, the content being operator config rather than a {@code MessageKey}, fails the
 * build instead of shipping as a literal {@code <accent>} in chat.
 */
class CommunicationDefaultContentStyleTest {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** Representative restyled lines, copied verbatim from the shipped {@code modules/communication} defaults. */
    private static final String INFO_TITLE = "<h:'Welcome to the server'> <value>{player}</value>!";

    private static final String INFO_LINK = "<cta>/rules</cta> <muted>·</muted> <body>read the server rules.</body>";
    private static final String ADVANCEMENT =
            "<tag:'NEWS'> <value>{player}</value> <body>has made the advancement</body> <good>[{title}]</good>";

    @Test
    void theInfoPageValueTokenResolvesToCyanAndLeavesNoLiteralToken() {
        Component rendered = MINI.deserialize(INFO_TITLE, StyleTags.resolver());
        assertThat(colours(rendered)).contains(StyleTags.value());
        assertNoLiteralTokens(rendered);
    }

    @Test
    void theInfoPageCtaAndBodyTokensResolveToTheirColours() {
        Component rendered = MINI.deserialize(INFO_LINK, StyleTags.resolver());
        assertThat(colours(rendered)).contains(StyleTags.cta(), StyleTags.muted(), StyleTags.body());
        assertNoLiteralTokens(rendered);
    }

    @Test
    void theAdvancementTemplateResolvesItsPrefixValueBodyAndGoodTokens() {
        // The HudText path: PlaceholderAPI pre-parse is the identity for this template (no %papi%), then this parse.
        Component rendered = MINI.deserialize(ADVANCEMENT, StyleTags.resolver());
        String plain = PLAIN.serialize(rendered);
        assertThat(plain).startsWith("ɴᴇᴡꜱ ▶"); // <tag:'NEWS'> renders the category prefix
        assertThat(colours(rendered)).contains(StyleTags.accent(), StyleTags.body(), StyleTags.good());
        assertNoLiteralTokens(rendered);
    }

    /** Every colour applied anywhere in the rendered component tree. */
    private static Set<TextColor> colours(Component root) {
        Set<TextColor> seen = new java.util.HashSet<>();
        Deque<Component> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Component node = queue.removeFirst();
            TextColor colour = node.color();
            if (colour != null) {
                seen.add(colour);
            }
            queue.addAll(node.children());
        }
        return seen;
    }

    /** A token that failed to resolve would survive as literal text (e.g. "<accent>") or a legacy "&a" code. */
    private static void assertNoLiteralTokens(Component rendered) {
        String plain = PLAIN.serialize(rendered);
        assertThat(plain).doesNotContain("<value>", "<accent>", "<body>", "<cta>", "<muted>", "<good>", "<tag:", "<h:");
        assertThat(plain).doesNotContain("&");
    }
}
