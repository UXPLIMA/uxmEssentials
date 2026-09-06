package com.uxplima.uxmessentials.villagers.domain;

import java.util.Objects;

/**
 * The villager-saver rule: given whether a villager carries the per-villager "protected" mark and the
 * {@link VillagerThreat} about to end it, decide whether that end must be cancelled. This is the whole of the
 * protection decision, the live {@code Villager}, the Bukkit event, and the PDC flag are adapter concerns; the
 * domain owns only the boolean logic so it can be unit-tested without Bukkit.
 *
 * <p>A villager is <em>in scope</em> for protection when it is individually marked or when {@code all} is set (the
 * operator chose to shield every villager, not only flagged ones). A threat is cancelled only when the feature is
 * {@code enabled}, the villager is in scope, and the per-threat gate for that threat is on, so an operator can
 * enable protection yet still let, say, zombie conversions through by turning {@code fromZombies} off. With the
 * feature disabled, or the villager out of scope, nothing is ever cancelled.
 *
 * @param enabled whether the protection feature runs at all
 * @param all whether every villager is protected, not only individually flagged ones
 * @param fromZombies whether {@link VillagerThreat#ZOMBIE_CONVERSION} is cancelled
 * @param fromLightning whether {@link VillagerThreat#LIGHTNING} is cancelled
 * @param fromDamage whether {@link VillagerThreat#DAMAGE} is cancelled
 * @param noDespawn whether {@link VillagerThreat#DESPAWN} is prevented
 */
public record VillagerProtectionPolicy(
        boolean enabled,
        boolean all,
        boolean fromZombies,
        boolean fromLightning,
        boolean fromDamage,
        boolean noDespawn) {

    /** Whether a villager (marked or not) should have {@code threat} cancelled under this policy. */
    public boolean cancels(boolean protectedMark, VillagerThreat threat) {
        Objects.requireNonNull(threat, "threat");
        if (!enabled || !inScope(protectedMark)) {
            return false;
        }
        return switch (threat) {
            case ZOMBIE_CONVERSION -> fromZombies;
            case LIGHTNING -> fromLightning;
            case DAMAGE -> fromDamage;
            case DESPAWN -> noDespawn;
        };
    }

    /** Whether a villager (marked or not) should be kept loaded rather than despawn under this policy. */
    public boolean protectsDespawn(boolean protectedMark) {
        return cancels(protectedMark, VillagerThreat.DESPAWN);
    }

    private boolean inScope(boolean protectedMark) {
        return all || protectedMark;
    }
}
