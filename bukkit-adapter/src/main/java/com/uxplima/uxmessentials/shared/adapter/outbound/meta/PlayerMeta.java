package com.uxplima.uxmessentials.shared.adapter.outbound.meta;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * A small, typed accessor over an online {@link Player}'s {@link org.bukkit.persistence.PersistentDataContainer},
 * storing every value as a {@link PersistentDataType#STRING}. The Phase-3 {@code has-meta} requirement reads it and
 * the Phase-7 anti-dupe marking writes it; {@link #add} layers a parse-then-store numeric view on top so a meta key
 * can be used as a counter.
 *
 * <p>This is for <b>online players only</b>. An offline player's PDC is not reachable through the Bukkit API
 * without NMS, which is out of scope here. Durable, server-authoritative data belongs in the database-backed
 * {@code PlayerDataStore}; PDC is the right home only for transient per-holder state.
 *
 * <h2>NamespacedKey discipline</h2>
 * Operator-chosen key names are open-ended, so the {@link NamespacedKey} for each is built once on first use and
 * cached in a {@link ConcurrentHashMap}. The hot-path rule from CLAUDE.md forbids constructing a
 * {@link NamespacedKey} per call. Every key lives under the one {@code uxmessentials} namespace, with the name
 * segment sanitised to the legal {@code [a-z0-9._-]} alphabet.
 */
public final class PlayerMeta {

    private final Plugin plugin;
    private final ConcurrentHashMap<String, NamespacedKey> keys = new ConcurrentHashMap<>();

    public PlayerMeta(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** The string value stored under {@code key}, or empty when the player has no such meta. */
    public Optional<String> get(Player player, String key) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(player.getPersistentDataContainer().get(keyFor(key), PersistentDataType.STRING));
    }

    /** Store {@code value} under {@code key}, overwriting any previous value. */
    public void set(Player player, String key, String value) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        player.getPersistentDataContainer().set(keyFor(key), PersistentDataType.STRING, value);
    }

    /**
     * Add {@code delta} to the value stored under {@code key} (treated as zero when missing or non-numeric), store
     * the result and return the new value.
     */
    public double add(Player player, String key, double delta) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        double next = parse(get(player, key).orElse(null)) + delta;
        set(player, key, format(next));
        return next;
    }

    /** Remove {@code key} for {@code player}; a no-op when it is absent. */
    public void remove(Player player, String key) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        player.getPersistentDataContainer().remove(keyFor(key));
    }

    /** Whether a string meta is stored under {@code key} for {@code player}. */
    public boolean has(Player player, String key) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(key, "key");
        return player.getPersistentDataContainer().has(keyFor(key), PersistentDataType.STRING);
    }

    /** The cached {@link NamespacedKey} for {@code key}, built once on first use (package-private for the test). */
    NamespacedKey keyFor(String key) {
        return keys.computeIfAbsent(key, name -> new NamespacedKey(plugin, sanitize(name)));
    }

    private static double parse(@org.jspecify.annotations.Nullable String raw) {
        if (raw == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException notNumeric) {
            return 0.0;
        }
    }

    /** Render a whole number without a trailing {@code .0} so a counter reads as {@code 5} rather than {@code 5.0}. */
    private static String format(double value) {
        if (Double.isFinite(value) && value == Math.floor(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /** A {@link NamespacedKey} value segment accepts only {@code [a-z0-9._-]}; fold anything else to {@code _}. */
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
