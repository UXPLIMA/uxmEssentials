package com.uxplima.uxmessentials.shared.application.port;

import java.util.List;
import java.util.Objects;

/**
 * Read access to the typed configuration tree, addressed by dotted HOCON paths
 * ({@code modules.homes.enabled}). The concrete implementation loads the HOCON files once on enable
 * and swaps the parsed tree atomically on {@link #reload()} (an {@code AtomicReference<Config>} swap
 * behind the adapter), so a reader either sees the whole previous tree or the whole new one, never a
 * half-applied config. The kernel depends only on this narrow contract so application code never
 * touches Configurate types directly.
 *
 * <p>Each {@code getX} takes a default returned when the path is absent, which is how a feature
 * module's {@code enabled} flag defaults to {@code true} when an operator has not declared it in
 * {@code modules.conf}. The numeric and list getters carry default implementations atop the three
 * primitive getters so an existing adapter keeps satisfying the contract; an adapter backed by a real
 * typed tree overrides them for native typing.
 */
public interface ConfigStore {

    /** Returns the boolean at {@code path}, or {@code fallback} when it is absent. */
    boolean getBoolean(String path, boolean fallback);

    /** Returns the string at {@code path}, or {@code fallback} when it is absent. */
    String getString(String path, String fallback);

    /** Returns the int at {@code path}, or {@code fallback} when it is absent. */
    int getInt(String path, int fallback);

    /** Returns the long at {@code path}, or {@code fallback} when it is absent. */
    default long getLong(String path, long fallback) {
        long probe = fallback == Long.MIN_VALUE ? Long.MAX_VALUE : Long.MIN_VALUE;
        long read = getInt(path, (int) probe);
        return read == (int) probe ? fallback : read;
    }

    /** Returns the double at {@code path}, or {@code fallback} when it is absent. */
    default double getDouble(String path, double fallback) {
        String raw = getString(path, "");
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Returns the string list at {@code path}, or {@code fallback} when it is absent. */
    default List<String> getStringList(String path, List<String> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return List.copyOf(fallback);
    }

    /**
     * Re-read the backing HOCON files and swap the parsed tree atomically. Implementations load once
     * on enable and replace the whole tree here; this method is the single mutation point so config
     * reads stay lock-free. The default is a no-op for a fixed in-memory store.
     */
    default void reload() {
        // No-op for an immutable store; a file-backed adapter overrides to swap its tree atomically.
    }

    /**
     * A view of this store rooted at {@code prefix}, so a module reads {@code default-warmup} against
     * its own subtree rather than the absolute {@code modules.teleport.default-warmup}. This is how
     * {@link com.uxplima.uxmessentials.shared.application.module.ModuleContext#config()} is scoped to
     * the module's {@code configRoot}.
     */
    default ConfigStore scoped(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        if (prefix.isEmpty()) {
            return this;
        }
        return new ScopedConfigStore(this, prefix);
    }

    /** Returns the child keys of the map at {@code path}, or an empty list if not a map/absent. */
    default List<String> getKeys(String path) {
        return List.of();
    }
}
