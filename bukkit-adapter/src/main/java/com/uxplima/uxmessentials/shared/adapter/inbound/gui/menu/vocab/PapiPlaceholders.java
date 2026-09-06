package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import me.clip.placeholderapi.PlaceholderAPI;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges PlaceholderAPI tokens into the menu engine's {@code %token%} syntax. Registered once at startup, it sets
 * the {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry} fallback so any
 * token written {@code %papi_<name>%} resolves through PlaceholderAPI's {@code %<name>%} expansion against the
 * viewer: letting an operator reuse the thousands of placeholders other plugins expose without a per-token binding.
 *
 * <p>PlaceholderAPI is a soft dependency, reached only past {@link PlaceholderApiSupport#isPresent()} so a server
 * without it never resolves the {@code me.clip.placeholderapi} classes and the bridge is an inert empty-string
 * resolver. An offline viewer resolves to empty too; the bridge never throws, mirroring how the message sink's
 * pre-parse bridge fails soft when the plugin or the player is absent.
 */
@NullMarked
public final class PapiPlaceholders {

    /** The token prefix this bridge claims; {@code %papi_foo%} becomes PlaceholderAPI's {@code %foo%}. */
    private static final String PREFIX = "papi_";

    private PapiPlaceholders() {}

    /**
     * Register the {@code papi_*} placeholder fallback into {@code bindings}. The fallback claims every id starting
     * with {@code papi_} so {@code MenuBindings.validate} accepts such a spec, and resolves it to the live token
     * value (or empty when PlaceholderAPI is absent or the viewer is offline).
     */
    public static void registerInto(MenuBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        bindings.placeholders().fallback(id -> id.startsWith(PREFIX), PapiPlaceholders::resolve);
    }

    /**
     * Expand one claimed {@code papi_<name>} id against the viewer. Returns empty when PlaceholderAPI is not
     * installed or the viewer is not online, so a placeholder a server cannot answer leaves the text blank rather
     * than aborting the render or leaking the raw token.
     */
    private static String resolve(String id, MenuContext ctx) {
        if (!PlaceholderApiSupport.isPresent()) {
            return "";
        }
        Player player = Bukkit.getPlayer(ctx.viewer().uuid());
        if (player == null) {
            return "";
        }
        return PlaceholderAPI.setPlaceholders(player, "%" + id.substring(PREFIX.length()) + "%");
    }
}
