package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a {@link MessageKey} into an Adventure {@link Component} in the viewer's locale, rendering the
 * catalog entry through {@link StyledText} so a menu's item names, lore, and title pick up the same style
 * tokens the chat sink uses. The shared management-GUI framework (and any module view that opts in) holds one
 * instance, so there is exactly one place a key turns into text and every label reads identically wherever it
 * is raised. This is the framework-wide generalisation of the per-module text helpers (e.g. the worlds
 * editor's own), so a new module's management GUI needs no bespoke text adapter.
 */
@NullMarked
public final class GuiText {

    private final Messages messages;

    public GuiText(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Resolve {@code key} for {@code viewer} with no placeholders. */
    public Component text(PlayerRef viewer, MessageKey key) {
        return text(viewer, key, Map.of());
    }

    /** Resolve {@code key} for {@code viewer} with {@code placeholders} substituted. */
    public Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    /**
     * Resolve {@code key} for {@code viewer} with the leading brand prefix ({@code <tag:'…'>} / {@code <etag:'…'>})
     * removed before rendering. Catalog prompt keys carry the chat prefix, but an inventory title (such as an anvil
     * prompt) must read clean (just the prompt, no {@code uxmEssentials »} brand) so this strips that one leading
     * token. A key with no prefix is rendered unchanged.
     */
    public Component unprefixedText(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        return StyledText.render(stripBrandPrefix(messages.resolve(viewer, key, placeholders)));
    }

    /** A leading {@code <tag:'…'>} or {@code <etag:'…'>} brand prefix token and the space after it, if present. */
    private static final java.util.regex.Pattern BRAND_PREFIX =
            java.util.regex.Pattern.compile("^\\s*<(?:tag|etag):'[^']*'>\\s*");

    /** Remove a leading brand-prefix token from {@code source}; returns it unchanged when there is none. */
    static String stripBrandPrefix(String source) {
        Objects.requireNonNull(source, "source");
        return BRAND_PREFIX.matcher(source).replaceFirst("");
    }
}
