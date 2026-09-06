package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import org.jspecify.annotations.NullMarked;

/**
 * Expands a speaker's PlaceholderAPI {@code %token%} placeholders in the public chat format before MiniMessage
 * parses it, keeping PlaceholderAPI a soft dependency exactly as the message sink's bridge does. When
 * PlaceholderAPI is installed the {@link #create()} factory returns a bridge over
 * {@link PlaceholderApiSupport#messageBridge(UUID)} (the same seam the announcer and catalog lines expand
 * through); when it is absent {@link #identity()} leaves the format untouched, so a {@code %token%} an operator
 * authored survives verbatim rather than blanking out.
 *
 * <p>The expansion runs on the async chat thread where the renderer already reads the speaker's cached state, and
 * it only substitutes text into a string. It touches no unsafe Bukkit world state, so the async-listener contract
 * holds. PlaceholderAPI is reached only through {@link PlaceholderApiSupport}, so this class carries no
 * {@code me.clip.placeholderapi} import and a server without the plugin never resolves those symbols.
 */
@NullMarked
public interface ChatPlaceholderExpander {

    /** {@code text} with {@code who}'s {@code %token%} placeholders expanded, or {@code text} unchanged when none apply. */
    String expand(UUID who, String text);

    /** An expander that returns the text unchanged: the fallback when PlaceholderAPI is not installed. */
    static ChatPlaceholderExpander identity() {
        return (who, text) -> text;
    }

    /** The PlaceholderAPI-backed expander when the plugin is installed (probed once), otherwise {@link #identity()}. */
    static ChatPlaceholderExpander create() {
        if (!PlaceholderApiSupport.isPresent()) {
            return identity();
        }
        return (who, text) -> {
            Objects.requireNonNull(who, "who");
            Objects.requireNonNull(text, "text");
            return PlaceholderApiSupport.messageBridge(who).apply(text);
        };
    }
}
