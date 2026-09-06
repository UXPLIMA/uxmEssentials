package com.uxplima.uxmessentials.tablist.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Where a tablist format's custom skin comes from. A skin is the one tab-list thing native Paper cannot do, to give a
 * viewer's tab row a different texture the adapter re-adds the entry through a player-info packet carrying the texture,
 * so a format may name where that texture is sourced from. This is pure operator intent: the domain only models the two
 * shapes, the adapter resolves them to a real texture (reading an online player's profile, or fetching an offline name)
 * and sends the packet.
 *
 * <p>Two shapes, a sealed pair so the adapter switch is exhaustive:
 *
 * <ul>
 *   <li>{@link Texture}: a base64-encoded texture value (and an optional signature) authored directly in config. No
 *       lookup is needed; the value is used as-is.</li>
 *   <li>{@link PlayerName}, the name of a player whose skin to copy. The adapter reads the named player's texture from
 *       their live profile when they are online, else fetches it for an offline name.</li>
 * </ul>
 */
public sealed interface TablistSkinSource permits TablistSkinSource.Texture, TablistSkinSource.PlayerName {

    /**
     * A skin given directly as a Mojang texture property: the base64-encoded {@code value} and the optional Yggdrasil
     * {@code signature}. The adapter passes these straight through to the packet with no lookup.
     *
     * @param value the base64-encoded texture payload, non-blank
     * @param signature the property signature, or empty for an unsigned texture
     */
    record Texture(String value, Optional<String> signature) implements TablistSkinSource {
        public Texture {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(signature, "signature");
            if (value.isBlank()) {
                throw new IllegalArgumentException("a tablist skin texture value must not be blank");
            }
        }
    }

    /**
     * A skin sourced by copying a named player's texture. The adapter reads the texture from the named player's live
     * profile when they are online, otherwise fetches it by name; a miss falls back to no skin (the native path).
     *
     * @param name the player whose skin to copy, non-blank
     */
    record PlayerName(String name) implements TablistSkinSource {
        public PlayerName {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("a tablist skin player name must not be blank");
            }
        }
    }
}
