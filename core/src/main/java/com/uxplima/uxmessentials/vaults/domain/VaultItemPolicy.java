package com.uxplima.uxmessentials.vaults.domain;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The set of materials a vault refuses to store, matched case-insensitively. Material names are plain strings
 * (e.g. {@code "bedrock"}) so the model stays free of any Bukkit type: the adapter maps each {@code ItemStack}
 * to its {@code Material.name()} and asks {@link #isBlocked(String)} before the contents are saved, returning
 * any blocked item to the player. The set is normalised once on construction. Every entry lowercased and
 * trimmed, so configured {@code "Bedrock"} and a queried {@code "BEDROCK"} compare equal.
 *
 * @param blockedMaterials the lowercased, trimmed material names to refuse; never {@code null} and immutable
 */
public record VaultItemPolicy(Set<String> blockedMaterials) {

    public VaultItemPolicy {
        Objects.requireNonNull(blockedMaterials, "blockedMaterials");
        blockedMaterials = blockedMaterials.stream()
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .collect(toUnmodifiableSet());
    }

    /** A policy that refuses nothing: every material is storable. */
    public static VaultItemPolicy allowAll() {
        return new VaultItemPolicy(Set.of());
    }

    /**
     * Whether a material is refused, comparing case-insensitively against the configured set.
     *
     * @param materialName the material name (e.g. {@code Material.name()}); a {@code null} name is never blocked
     * @return {@code true} when the material is on the blacklist
     */
    public boolean isBlocked(@Nullable String materialName) {
        return materialName != null && blockedMaterials.contains(materialName.toLowerCase(Locale.ROOT));
    }
}
