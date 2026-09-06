package com.uxplima.uxmessentials.villagers.domain;

/**
 * The kinds of end a protected villager can be shielded from, named in the domain's own vocabulary so the
 * protection rule can be decided without any Bukkit event on the classpath. The adapter maps each concrete
 * Bukkit cause onto one of these before consulting {@link VillagerProtectionPolicy}: a zombie infection
 * {@code EntityTransformEvent} becomes {@link #ZOMBIE_CONVERSION}, a lightning strike (its damage and its
 * villager-to-witch transform) becomes {@link #LIGHTNING}, every other {@code EntityDamageEvent}, suffocation,
 * fire, a mob's blow, becomes {@link #DAMAGE}, and the natural despawn / far-away removal becomes
 * {@link #DESPAWN}.
 */
public enum VillagerThreat {

    /** A zombie infecting the villager into a zombie villager. */
    ZOMBIE_CONVERSION,

    /** A lightning strike: its damage and the villager-to-witch transform it triggers. */
    LIGHTNING,

    /** Any other lethal damage: suffocation, fire, drowning, a mob's or player's blow. */
    DAMAGE,

    /** Natural despawn or removal when no player is nearby. */
    DESPAWN
}
