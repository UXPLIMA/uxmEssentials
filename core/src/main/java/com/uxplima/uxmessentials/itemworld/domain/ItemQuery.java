package com.uxplima.uxmessentials.itemworld.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * A validated, namespaced item identifier as accepted by {@code /give}, {@code /item} and {@code /itemdb}.
 *
 * <p>The domain does not know Bukkit {@code Material}. It only normalises the textual id to the canonical
 * lowercase, {@code namespace:path} shape the adapter then resolves against the live registry. A bare id
 * without a namespace defaults to {@code minecraft:}. The grammar here rejects obviously malformed ids
 * (blank, illegal characters) so the adapter's registry lookup is the only remaining failure mode; an id
 * that is well-formed here but unknown to the registry maps to {@link ItemWorldError#UNKNOWN_ITEM} at the
 * boundary.
 *
 * @param namespace the resource namespace, lowercased ({@code minecraft})
 * @param path the resource path, lowercased ({@code diamond_sword})
 */
public record ItemQuery(String namespace, String path) {

    private static final String DEFAULT_NAMESPACE = "minecraft";

    public ItemQuery {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!isValidSegment(namespace)) {
            throw new IllegalArgumentException("invalid item namespace: " + namespace);
        }
        if (!isValidSegment(path)) {
            throw new IllegalArgumentException("invalid item path: " + path);
        }
    }

    /**
     * Parse {@code raw} into a query, defaulting the namespace to {@code minecraft} when absent. Returns
     * empty for a blank or malformed id rather than throwing, so a use case maps it to
     * {@link ItemWorldError#UNKNOWN_ITEM}.
     */
    public static java.util.Optional<ItemQuery> parse(String raw) {
        if (raw == null) {
            return java.util.Optional.empty();
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return java.util.Optional.empty();
        }
        int colon = trimmed.indexOf(':');
        String namespace = colon < 0 ? DEFAULT_NAMESPACE : trimmed.substring(0, colon);
        String path = colon < 0 ? trimmed : trimmed.substring(colon + 1);
        if (!isValidSegment(namespace) || !isValidSegment(path)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ItemQuery(namespace, path));
    }

    /** The canonical {@code namespace:path} form the adapter resolves against the registry. */
    public String asKey() {
        return namespace + ":" + path;
    }

    private static boolean isValidSegment(String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            boolean ok =
                    (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-' || c == '/';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
