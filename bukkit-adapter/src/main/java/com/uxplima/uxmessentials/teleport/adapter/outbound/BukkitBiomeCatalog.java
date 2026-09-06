package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;

import com.uxplima.uxmessentials.teleport.application.port.BiomeCatalog;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The production {@link BiomeCatalog}: it resolves an operator-typed biome key against the server's biome registry
 * and normalises the result to the same lower-cased path form a validated candidate carries ({@code plains},
 * {@code desert}), so the {@code /rtp biome} biome gate compares like for like. An input with no matching registry
 * entry (a typo, or a biome the running version does not have) resolves to empty, and the use case reports it.
 *
 * <p>The key list is built once per call for tab completion; it is a cheap registry walk, not a hot path (Brigadier
 * only asks while the player is typing the argument).
 */
@NullMarked
public final class BukkitBiomeCatalog implements BiomeCatalog {

    @Override
    public Optional<BiomeName> resolve(String rawKey) {
        Objects.requireNonNull(rawKey, "rawKey");
        String trimmed = rawKey.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        NamespacedKey key = NamespacedKey.fromString(trimmed);
        Biome biome = key == null ? null : lookup(key);
        return biome == null
                ? Optional.empty()
                : Optional.of(BiomeName.of(biome.getKey().getKey()));
    }

    @Override
    public List<String> keys() {
        return biomes().map(biome -> biome.getKey().getKey()).sorted().toList();
    }

    // Registry.BIOME is deprecated (since 1.21.3, not for removal) in favour of RegistryAccess, but it remains the
    // public, MockBukkit-backed way to resolve/iterate biomes by key on the 1.21+ line (mirrors ConstantBiomeProvider).
    @SuppressWarnings("deprecation")
    private static @Nullable Biome lookup(NamespacedKey key) {
        return Registry.BIOME.get(key);
    }

    // Same deprecated-but-live Registry.BIOME surface as lookup above; see the note there.
    @SuppressWarnings("deprecation")
    private static java.util.stream.Stream<Biome> biomes() {
        return Registry.BIOME.stream();
    }
}
