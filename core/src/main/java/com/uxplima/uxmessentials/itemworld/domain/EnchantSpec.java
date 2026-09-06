package com.uxplima.uxmessentials.itemworld.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A validated enchantment request for {@code /enchant <enchant> [level]}: a normalised enchantment id and a
 * level clamped to a configured ceiling.
 *
 * <p>The domain does not know Bukkit {@code Enchantment}. It normalises the id to the canonical lowercase
 * {@code namespace:path} shape and the adapter resolves it. The level is clamped at the boundary: an operator
 * sets a {@code max-enchant-level} in {@code itemworld.conf} (docs/09-deployment.md §itemworld.conf) so an
 * over-vanilla level like {@code sharpness 32767} is bounded rather than handed to the registry verbatim.
 * Whether the clamp engaged is reported, so the actor can be told the level was reduced
 * ({@link ItemworldMessageKey#ENCHANT_LEVEL_CLAMPED}).
 *
 * @param enchantId the normalised {@code namespace:path} enchantment id
 * @param level the clamped level (always {@code >= 1})
 * @param clamped whether the requested level exceeded the cap and was reduced
 */
public record EnchantSpec(String enchantId, int level, boolean clamped) {

    /** The level ceiling applied when an operator leaves {@code max-enchant-level} unset. */
    public static final int DEFAULT_MAX_LEVEL = 32_767;

    private static final String DEFAULT_NAMESPACE = "minecraft";

    public EnchantSpec {
        Objects.requireNonNull(enchantId, "enchantId");
        if (level < 1) {
            throw new IllegalArgumentException("enchant level must be positive: " + level);
        }
    }

    /**
     * Normalise {@code rawId} and clamp {@code requestedLevel} to {@code maxLevel}. Returns empty for a blank
     * id (a use case maps that to {@link ItemWorldError#UNKNOWN_ENCHANT}); a non-positive level floors to 1, a
     * level above the cap clamps down and records {@link #clamped()}.
     */
    public static Optional<EnchantSpec> of(String rawId, int requestedLevel, int maxLevel) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        String normalised = normaliseId(rawId);
        int ceiling = maxLevel < 1 ? DEFAULT_MAX_LEVEL : maxLevel;
        int floored = Math.max(1, requestedLevel);
        int level = Math.min(floored, ceiling);
        return Optional.of(new EnchantSpec(normalised, level, level < floored));
    }

    /**
     * Resolve a requested level for the {@code /itemedit enchant} editor, honouring an over-vanilla-max allowance.
     * When {@code allowOverMax} is {@code false} the level is clamped to the enchantment's {@code vanillaMax} (the
     * value the registry reports for the resolved enchant); when {@code true} it is clamped only to the absolute
     * {@code hardCeiling} the operator sets in {@code max-enchant-level}. A blank id is empty and a level below one
     * floors to one, matching {@link #of}. {@link #clamped()} reports whether the requested level was reduced by
     * whichever ceiling applied.
     */
    public static Optional<EnchantSpec> forEdit(
            String rawId, int requestedLevel, int vanillaMax, boolean allowOverMax, int hardCeiling) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        String normalised = normaliseId(rawId);
        int hard = hardCeiling < 1 ? DEFAULT_MAX_LEVEL : hardCeiling;
        int ceiling = allowOverMax ? hard : Math.min(Math.max(1, vanillaMax), hard);
        int floored = Math.max(1, requestedLevel);
        int level = Math.min(floored, ceiling);
        return Optional.of(new EnchantSpec(normalised, level, level < floored));
    }

    private static String normaliseId(String rawId) {
        String trimmed = rawId.trim().toLowerCase(Locale.ROOT);
        return trimmed.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ":" + trimmed : trimmed;
    }
}
