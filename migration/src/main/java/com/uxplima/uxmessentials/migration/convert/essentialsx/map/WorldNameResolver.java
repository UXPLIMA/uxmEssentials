package com.uxplima.uxmessentials.migration.convert.essentialsx.map;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves an EssentialsX world name to a {@link WorldRef} (name plus persistent uid). EssentialsX
 * stores only a world name; the domain addresses a world by uid, so the mapper needs this seam to look
 * up the live world. The bukkit-side wiring backs it with the server's world list; a record whose world
 * the server does not know resolves to {@link Optional#empty()} and the mapper skips that location
 * (docs/12-migration §4: a missing world is a skipped entry, not a failed run).
 */
@NullMarked
@FunctionalInterface
public interface WorldNameResolver {

    /** The world for {@code name}, or empty when the server does not know it. */
    Optional<WorldRef> resolve(String name);
}
