package com.uxplima.uxmessentials.communication.domain;

/**
 * The cause of a player's death, in the communication context's own vocabulary. The key an operator selects a
 * per-cause death template by in {@code join-quit.conf}. It is a pure domain enum: the adapter maps a live Bukkit
 * death (its {@code DamageCause} plus who dealt the final blow) onto one of these, so the domain never sees a
 * Bukkit type.
 *
 * <p>Two of the values have no direct {@code DamageCause} counterpart and are decided by <em>who</em> struck the
 * final blow rather than <em>how</em>: {@link #PVP} is a death at another player's hand, {@link #MOB} a death at a
 * non-player creature's. The rest mirror the environmental damage kinds. {@link #OTHER} is the catch-all the
 * adapter falls back to for a cause it does not translate, and it carries no per-cause template of its own, a
 * death classified {@code OTHER} simply takes the default death policy.
 */
public enum DeathCause {

    /** Killed by another player. */
    PVP,

    /** Killed by a non-player creature striking directly. */
    MOB,

    /** Killed by a projectile (an arrow, a trident, a fireball). */
    PROJECTILE,

    /** Killed by fall damage. */
    FALL,

    /** Burned to death by fire or a fire tick. */
    FIRE,

    /** Killed by standing in lava. */
    LAVA,

    /** Drowned. */
    DROWNING,

    /** Killed by a block or entity explosion. */
    EXPLOSION,

    /** Fell out of the world / into the void. */
    VOID,

    /** Struck by lightning. */
    LIGHTNING,

    /** Suffocated inside a block. */
    SUFFOCATION,

    /** Starved to death. */
    STARVATION,

    /** Killed by poison. */
    POISON,

    /** Killed by a magic effect (a potion, an evoker fang). */
    MAGIC,

    /** Killed by the wither effect. */
    WITHER,

    /** Crushed by a falling block (an anvil, gravel). */
    FALLING_BLOCK,

    /** Killed by a thorns-enchanted retaliation. */
    THORNS,

    /** Killed by the ender dragon's breath. */
    DRAGON_BREATH,

    /** Flew into a wall while gliding (elytra kinetic damage). */
    FLY_INTO_WALL,

    /** Killed by standing on a hot block (magma). */
    HOT_FLOOR,

    /** Killed by contact damage (a cactus, a sweet-berry bush). */
    CONTACT,

    /** Froze to death in powder snow. */
    FREEZE,

    /** Killed by a warden's sonic boom. */
    SONIC_BOOM,

    /** Any cause the adapter does not translate; takes the default death policy. */
    OTHER
}
