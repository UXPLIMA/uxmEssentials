package com.uxplima.uxmessentials.shared.adapter.outbound.miniplaceholders;

import org.bukkit.Bukkit;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import io.github.miniplaceholders.api.MiniPlaceholders;
import org.jspecify.annotations.NullMarked;

/**
 * The single decision point for the MiniPlaceholders soft-depend. Every {@code io.github.miniplaceholders} symbol
 * is reached only through this class and only past {@link #isPresent()}, so a server without MiniPlaceholders never
 * resolves those classes and the plugin runs unchanged.
 *
 * <p>Unlike PlaceholderAPI, whose {@code %token%} values are a pre-parse string transform, MiniPlaceholders is
 * MiniMessage-native: it contributes {@link TagResolver}s that MiniMessage consults <em>during</em> {@code
 * deserialize}. {@link #globalResolver()} returns the server-global resolver (audience-free tags like {@code
 * <server_online>}/{@code <server_tps>}) when the plugin is present, or {@link TagResolver#empty()}, a no-op that
 * makes {@code deserialize(text, resolver)} identical to the plain parse: when it is absent. Audience-relative
 * MiniPlaceholders (per-viewer tags) are a separate concern that would drive per-viewer rendering, as the
 * per-viewer PlaceholderAPI path does; this support exposes only the global resolver.
 */
@NullMarked
public final class MiniPlaceholdersSupport {

    private static final String PLUGIN_NAME = "MiniPlaceholders";

    private MiniPlaceholdersSupport() {}

    /** True when the MiniPlaceholders plugin is installed on this server. */
    public static boolean isPresent() {
        return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME) != null;
    }

    /**
     * The server-global MiniPlaceholders tag resolver when the plugin is present, else {@link TagResolver#empty()}.
     * The {@code MiniPlaceholders} class is referenced only past the {@link #isPresent()} guard, so it is never
     * loaded on a server without the plugin; an empty resolver leaves {@code deserialize} unchanged there.
     */
    public static TagResolver globalResolver() {
        if (!isPresent()) {
            return TagResolver.empty();
        }
        return MiniPlaceholders.globalPlaceholders();
    }
}
