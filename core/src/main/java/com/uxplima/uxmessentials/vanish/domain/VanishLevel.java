package com.uxplima.uxmessentials.vanish.domain;

/**
 * The vanish tier a hidden player is at: their <em>use level</em> in PremiumVanish terms. A viewer sees a vanished
 * player only when the viewer's <em>see level</em> is at least this use level ({@link VanishLevels#sees}), so a higher
 * level "more hidden": fewer viewers clear the bar. Levels are 1-based. Plain {@code uxmessentials.vanish.use} (no
 * numbered node) is {@link #DEFAULT} level 1, which is the whole story when layered permissions are off.
 *
 * <p>The level is resolved from the target's permissions by the adapter and carried through the state and the store;
 * the domain never reads a permission, it only compares two already-resolved levels. The minimum is {@link #MIN_LEVEL}
 * (a vanished player is always at least level 1, so a viewer with no see level at all, see level 0, never sees them);
 * the invariant is enforced here so an out-of-range level can never enter the store.
 *
 * @param level the use level, {@code 1} for the default; higher hides from more viewers
 */
public record VanishLevel(int level) {

    /** The lowest use level a vanished player can hold; a plainly-vanished player sits here. */
    public static final int MIN_LEVEL = 1;

    /** The default level: a player vanished behind the plain {@code uxmessentials.vanish.use} node. */
    public static final VanishLevel DEFAULT = new VanishLevel(MIN_LEVEL);

    public VanishLevel {
        if (level < MIN_LEVEL) {
            throw new IllegalArgumentException("level must be >= " + MIN_LEVEL + ": " + level);
        }
    }

    /** The level {@code level}, clamped up to {@link #MIN_LEVEL} so a sub-1 resolution never breaks the invariant. */
    public static VanishLevel of(int level) {
        return new VanishLevel(Math.max(level, MIN_LEVEL));
    }
}
