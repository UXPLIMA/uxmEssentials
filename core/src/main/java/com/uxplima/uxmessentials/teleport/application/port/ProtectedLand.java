package com.uxplima.uxmessentials.teleport.application.port;

import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * A player-agnostic "is this spot protected land?" seam the random-teleport safe-search consults so its shared
 * pre-warmed pool never drops a player inside a land claim or a WorldGuard region. Because the pool is shared
 * there is no owning player to run an access check against. The question is purely spatial: does <em>anyone's</em>
 * claim, or any region, cover this position?
 *
 * <p>The safe-search adapter answers this on the region thread it already holds after the async chunk load, so the
 * check never adds a scheduler hop. The concrete backends live in the adapter: the shared claim seam and a
 * reflective WorldGuard region check, composed behind the two {@code respect-*} toggles by {@link
 * com.uxplima.uxmessentials.teleport.application.ClaimAwareProtectedLand}. Everything is fail-open, a server with no
 * region plugin, or a check that is switched off, reports "not protected", the graceful default {@link #NONE}
 * embodies.
 */
@NullMarked
@FunctionalInterface
public interface ProtectedLand {

    /** Whether {@code where} falls inside a land claim or a protected region and should be avoided. */
    boolean isProtected(Position where);

    /** Never protects: the default when no region plugin is present or protection avoidance is disabled. */
    ProtectedLand NONE = where -> false;
}
