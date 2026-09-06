package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.generator.ChunkGenerator;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.domain.BiomeId;
import com.uxplima.uxmessentials.worlds.domain.BuiltInGenerators;
import com.uxplima.uxmessentials.worlds.domain.FlatLayerPlan;
import org.jspecify.annotations.NullMarked;

/**
 * Maps a built-in generator id ({@code void}/{@code flat}, case-insensitive) to the matching
 * {@link ChunkGenerator}. Both generators are built <em>once</em> at construction from the resolved
 * config (the immutable flat plan and the two biomes) and held in final fields, so the resolver and
 * the generators it hands out are stateless beyond that snapshot. Safe to share across Paper's
 * parallel, off-tick worldgen threads.
 *
 * <p>This is the single handle both {@code BukkitWorldEngine} (internal {@code /worlds create}) and the
 * plugin's {@code getDefaultWorldGenerator} hook ({@code bukkit.yml}, the default world included, and worlds
 * another plugin creates) call, so a {@code uxmEssentials:void|flat} ref resolves identically on either path.
 */
@NullMarked
public final class WorldGeneratorResolver {

    private final ChunkGenerator voidGen;
    private final ChunkGenerator flatGen;

    public WorldGeneratorResolver(FlatLayerPlan flatPlan, BiomeId voidBiome, BiomeId flatBiome, Logger log) {
        Objects.requireNonNull(flatPlan, "flatPlan");
        Objects.requireNonNull(voidBiome, "voidBiome");
        Objects.requireNonNull(flatBiome, "flatBiome");
        Objects.requireNonNull(log, "log");
        this.voidGen = new VoidChunkGenerator(ConstantBiomeProvider.from(voidBiome, log));
        this.flatGen = FlatChunkGenerator.from(flatPlan, ConstantBiomeProvider.from(flatBiome, log), log);
    }

    /** The void generator for {@code "void"}, the flat one for {@code "flat"} (case-insensitive); else empty. */
    public Optional<ChunkGenerator> resolve(String id) {
        Objects.requireNonNull(id, "id");
        return switch (id.toLowerCase(Locale.ROOT)) {
            case BuiltInGenerators.VOID -> Optional.of(voidGen);
            case BuiltInGenerators.FLAT -> Optional.of(flatGen);
            default -> Optional.empty();
        };
    }
}
