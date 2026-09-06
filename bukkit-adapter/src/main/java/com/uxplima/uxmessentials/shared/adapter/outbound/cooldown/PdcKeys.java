package com.uxplima.uxmessentials.shared.adapter.outbound.cooldown;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import org.jspecify.annotations.NullMarked;

/**
 * A small per-plugin cache of {@link NamespacedKey} instances keyed by a logical stamp name. The
 * cooldown adapter stamps under a key per feature (and per generic command label); since the feature
 * and label spaces are open, the keys cannot all be declared as constants. They are still built only
 * once each. The first request for a name constructs the {@link NamespacedKey}, every later request
 * for the same name returns the cached instance, so the "never on a hot path" invariant holds.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>concurrent-collection</b>. The cache is a {@link ConcurrentHashMap} populated via
 * {@code computeIfAbsent}; reads and writes are safe from any thread.
 */
@NullMarked
final class PdcKeys {

    private final Plugin plugin;
    private final String prefix;
    private final ConcurrentHashMap<String, NamespacedKey> cache = new ConcurrentHashMap<>();

    PdcKeys(Plugin plugin, String prefix) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.prefix = Objects.requireNonNull(prefix, "prefix");
    }

    /** The cached key for {@code name}, building it once on first request. */
    NamespacedKey forName(String name) {
        Objects.requireNonNull(name, "name");
        return cache.computeIfAbsent(name, n -> new NamespacedKey(plugin, prefix + sanitize(n)));
    }

    /** A NamespacedKey value segment accepts only {@code [a-z0-9._-]}; fold anything else to {@code _}. */
    private static String sanitize(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean legal = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            out.append(legal ? c : '_');
        }
        return out.toString();
    }
}
