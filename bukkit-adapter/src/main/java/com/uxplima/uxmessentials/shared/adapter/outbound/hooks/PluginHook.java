package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import org.jspecify.annotations.NullMarked;

/**
 * One optional-plugin integration. A hook names the Bukkit plugin it integrates with, knows whether that
 * plugin is installed and enabled, and resolves to a typed capability {@code T} that always has a safe
 * absent default, so a caller holding the capability never null-checks or try/catches a missing plugin.
 *
 * <p>The soft-depend trap this contract is shaped to avoid is {@link NoClassDefFoundError}: a hook references
 * the target plugin's SDK types only inside {@link #whenPresent}, and {@link #whenAbsent} carries none of
 * them. {@link Hooks} calls {@code whenPresent} only once {@link #isPresent} reports true, so on a server
 * without the plugin the real implementation is never constructed and its external classes are never loaded.
 * This mirrors how the claim-provider adapters keep each SDK behind its own present-guard.
 *
 * @param <T> the capability interface callers consume; its absent default is a pure no-op
 */
@NullMarked
public interface PluginHook<T> {

    /** The Bukkit plugin name to integrate with: the same key declared as a soft-depend in paper-plugin.yml. */
    String pluginName();

    /** The capability type {@link Hooks#capability(Class)} is keyed by. */
    Class<T> capability();

    /**
     * The capability used when the plugin is absent: a complete no-op that references none of the plugin's
     * SDK types, so resolving an absent hook never loads an external class. Must never return {@code null}.
     */
    T whenAbsent();

    /**
     * The capability backed by the present, enabled plugin. This is the single place the plugin's SDK types
     * are referenced, and {@link Hooks} calls it only after {@link #isPresent} reports true. Must never
     * return {@code null}.
     */
    T whenPresent(Server server);

    /** True when the target plugin is installed and enabled on {@code server}. Stable for a server run. */
    default boolean isPresent(Server server) {
        Objects.requireNonNull(server, "server");
        Plugin plugin = server.getPluginManager().getPlugin(pluginName());
        return plugin != null && plugin.isEnabled();
    }
}
