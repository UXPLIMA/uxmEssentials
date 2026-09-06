package com.uxplima.uxmessentials.vaults.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The vault item blacklist policy, asserted in isolation: a configured material matches case-insensitively
 * regardless of the casing it was configured or queried in, a material that is not on the list is storable,
 * {@link VaultItemPolicy#allowAll()} refuses nothing, a {@code null} query is never blocked, and the policy
 * keeps a defensive immutable copy so mutating the source set after construction leaves it unchanged. The
 * model is pure, names are plain strings, so every rule holds here without a Bukkit {@code Material}.
 */
class VaultItemPolicyTest {

    @Test
    void blockedMaterialMatchesCaseInsensitively() {
        VaultItemPolicy policy = new VaultItemPolicy(Set.of("BEDROCK"));

        assertThat(policy.isBlocked("bedrock")).isTrue();
        assertThat(policy.isBlocked("BedRock")).isTrue();
        assertThat(policy.isBlocked("BEDROCK")).isTrue();
    }

    @Test
    void aMaterialNotOnTheListIsStorable() {
        VaultItemPolicy policy = new VaultItemPolicy(Set.of("bedrock"));

        assertThat(policy.isBlocked("diamond")).isFalse();
    }

    @Test
    void allowAllRefusesNothing() {
        VaultItemPolicy policy = VaultItemPolicy.allowAll();

        assertThat(policy.blockedMaterials()).isEmpty();
        assertThat(policy.isBlocked("bedrock")).isFalse();
    }

    @Test
    void aNullMaterialNameIsNeverBlocked() {
        VaultItemPolicy policy = new VaultItemPolicy(Set.of("bedrock"));

        assertThat(policy.isBlocked(null)).isFalse();
    }

    @Test
    void theBlockedSetIsADefensiveCopy() {
        Set<String> source = new HashSet<>();
        source.add("bedrock");
        VaultItemPolicy policy = new VaultItemPolicy(source);

        source.add("diamond");

        assertThat(policy.isBlocked("diamond")).isFalse();
        assertThat(policy.blockedMaterials()).containsExactly("bedrock");
    }

    @Test
    void configuredNamesAreNormalisedToLowercaseOnConstruction() {
        VaultItemPolicy policy = new VaultItemPolicy(Set.of("Bedrock"));

        assertThat(policy.isBlocked("bedrock")).isTrue();
        assertThat(policy.blockedMaterials()).containsExactly("bedrock");
    }
}
