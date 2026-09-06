package com.uxplima.uxmessentials.vanish.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * Resolves a player's PremiumVanish-style see and use levels from their permissions. Levels are derived, never stored:
 * the adapter reads {@code uxmessentials.vanish.use.level<N>} / {@code uxmessentials.vanish.see.level<N>} (plus the
 * plain {@code .use} / {@code .see} nodes as level 1, and op / {@code *} as the configured maximum), so a permission
 * change takes effect the next time a level is resolved rather than being pinned to a stored value.
 *
 * <p>The use level is what a vanishing player is hidden <em>at</em>, captured into the {@link VanishStore} when they
 * vanish and compared against every viewer's see level. The see level is what a viewer is measured <em>by</em> when the
 * pure {@code VanishState#canSee} rule decides whether they see a vanished target. When layered permissions are off
 * (the default) both collapse to the flat level-1 behaviour: use level 1 for everyone, see level 1 for a {@code .see}
 * holder and 0 for everyone else.
 */
public interface VanishLevelResolver {

    /** The use level {@code who} vanishes at, resolved from their permissions; never below {@link VanishLevel#MIN_LEVEL}. */
    VanishLevel useLevel(PlayerRef who);

    /**
     * The see level {@code who} is measured by, resolved from their permissions; {@code 0}
     * ({@link com.uxplima.uxmessentials.vanish.domain.VanishLevels#NO_SEE_LEVEL}) when they hold no vanish-see node.
     */
    int seeLevel(PlayerRef who);
}
