package com.uxplima.uxmessentials.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a player, decoupled from any Bukkit {@code Player} handle.
 *
 * <p>The kernel and every bounded context address players by this value object so application code
 * never holds a live entity reference across a thread or region hop. The adapter resolves the live
 * {@code Player} from the {@link #uuid()} at the boundary, and silently no-ops when the player is
 * offline. The display name is carried only as a convenience for rendering and audit attribution; it
 * is never used for identity or equality.
 *
 * @param uuid the player's stable account identifier
 * @param name the last-known display name, for rendering and audit lines
 */
public record PlayerRef(UUID uuid, String name) {

    /** Reserved UUID used for the server console and other non-player system actors. */
    public static final UUID SYSTEM_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public PlayerRef {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
    }

    /** Build a non-player actor reference suitable for attribution and console-facing notifications. */
    public static PlayerRef system(String name) {
        return new PlayerRef(SYSTEM_UUID, name);
    }

    /** Whether this reference identifies a non-player system actor rather than a Minecraft account. */
    public boolean isSystem() {
        return SYSTEM_UUID.equals(uuid);
    }

    /** Two refs are the same player when their UUIDs match; the name is informational only. */
    @Override
    public boolean equals(Object other) {
        return other instanceof PlayerRef ref && uuid.equals(ref.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
