package com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.ServerLinks;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One parsed, validated server-link entry, ready to push into Paper's {@link ServerLinks}. Exactly one of
 * {@code type} or {@code label} is set: a built-in {@link ServerLinks.Type} (the typed pause-menu slot) or a
 * custom operator-authored label (free text). The {@code url} is a validated absolute {@link URI}.
 *
 * <p>Parsing is total and never throws: a malformed entry. Both type and label missing, an unknown type name, a
 * blank or malformed URL, resolves to {@link Optional#empty()} so the applier skips it with a warning rather than
 * aborting the whole list. A custom label is operator content (config data), so it carries no inline plugin
 * literal of our own.
 *
 * @param type the built-in link type, or {@code null} when this is a custom-label entry
 * @param label the custom label text, or {@code null} when this is a typed entry
 * @param url the validated absolute destination
 */
@NullMarked
public record ServerLinkSpec(
        ServerLinks.@Nullable Type type, @Nullable String label, URI url) {

    public ServerLinkSpec(ServerLinks.@Nullable Type type, @Nullable String label, URI url) {
        this.url = Objects.requireNonNull(url, "url");
        if ((type == null) == (label == null)) {
            throw new IllegalArgumentException("exactly one of type or label must be set");
        }
        this.type = type;
        this.label = label;
    }

    /** Parse one entry from its raw {@code type}/{@code label}/{@code url} fields; empty when any is invalid. */
    public static Optional<ServerLinkSpec> parse(@Nullable String type, @Nullable String label, @Nullable String url) {
        Optional<URI> parsedUrl = parseUrl(url);
        if (parsedUrl.isEmpty()) {
            return Optional.empty();
        }
        URI destination = parsedUrl.get();
        Optional<ServerLinks.Type> parsedType = parseType(type);
        if (parsedType.isPresent()) {
            return Optional.of(new ServerLinkSpec(parsedType.get(), null, destination));
        }
        if (label != null && !label.isBlank()) {
            return Optional.of(new ServerLinkSpec(null, label.strip(), destination));
        }
        return Optional.empty();
    }

    private static Optional<ServerLinks.Type> parseType(@Nullable String type) {
        if (type == null || type.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ServerLinks.Type.valueOf(type.strip().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    private static Optional<URI> parseUrl(@Nullable String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url.strip());
            return uri.isAbsolute() ? Optional.of(uri) : Optional.empty();
        } catch (URISyntaxException malformed) {
            return Optional.empty();
        }
    }

    /** A short human description of this entry for the skip/apply log line. */
    public String describe() {
        return type != null ? type.name() : "\"" + label + "\"";
    }
}
