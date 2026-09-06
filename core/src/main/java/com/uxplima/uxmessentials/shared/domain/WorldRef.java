package com.uxplima.uxmessentials.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a world, decoupled from any Bukkit {@code World} handle.
 *
 * <p>A world is addressed by its persistent {@link #uid()} so a reference stays valid across a
 * reload, while {@link #name()} carries the operator-facing name used in config keys
 * ({@code teleport.spawn.mirror.<world>}), permission segments, and rendered messages. Equality is on
 * the uid alone: renaming a world folder does not change its identity.
 *
 * @param uid the world's persistent unique identifier
 * @param name the operator-facing world name
 */
public record WorldRef(UUID uid, String name) {

    public WorldRef {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorldRef ref && uid.equals(ref.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }
}
