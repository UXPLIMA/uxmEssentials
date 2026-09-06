package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.worlds.domain.FlatLayer;
import com.uxplima.uxmessentials.worlds.domain.FlatLayerPlan;
import org.jspecify.annotations.NullMarked;

/**
 * The built-in {@code uxmEssentials:flat} chunk generator: paints a world as an ordered stack of block
 * bands over a single fixed biome. The domain {@link FlatLayerPlan} is translated <em>once</em> at wire
 * time (via {@link #from}) into an immutable list of {@link ResolvedLayer}s, each carrying a pre-composed
 * {@link BlockData} that is reused for every chunk. Worldgen never resolves a material or allocates a
 * block per call.
 *
 * <p>All vanilla generation stages are suppressed ({@code shouldGenerate* → false}), so Paper writes
 * nothing before our hook runs. The bands are written in {@link #generateNoise}: the Paper contract is
 * that each {@code shouldGenerate*()} flag only gates the <em>vanilla</em> pass that runs <em>before</em>
 * the matching {@code generate*()} method: the overridden {@code generate*()} is always invoked. So with
 * noise suppressed our {@code generateNoise} receives empty {@link ChunkData} and fills the flat layers.
 *
 * <p>The generator holds only the immutable plan and the injected {@link BiomeProvider}, so it is safe
 * for Paper's parallel, off-tick worldgen threads.
 */
@NullMarked
public final class FlatChunkGenerator extends ChunkGenerator {

    /** One pre-composed band: its {@link BlockData} reused across chunks, repeated {@code height} times. */
    record ResolvedLayer(BlockData block, int height) {
        ResolvedLayer {
            Objects.requireNonNull(block, "block");
            if (height < 1) {
                throw new IllegalArgumentException("resolved layer height must be at least 1: " + height);
            }
        }
    }

    private final List<ResolvedLayer> plan;
    private final BiomeProvider biomeProvider;

    FlatChunkGenerator(List<ResolvedLayer> plan, BiomeProvider biomeProvider) {
        this.plan = List.copyOf(Objects.requireNonNull(plan, "plan"));
        this.biomeProvider = Objects.requireNonNull(biomeProvider, "biomeProvider");
    }

    /**
     * Resolves a domain {@link FlatLayerPlan} into an allocation-careful generator: each {@link FlatLayer}'s
     * block id is matched to a {@link Material} and its {@link BlockData} composed once here. An unknown
     * block id falls back to {@link Material#STONE}, warned once at wire time (never on the gen hot path).
     */
    static FlatChunkGenerator from(FlatLayerPlan plan, BiomeProvider biomeProvider, Logger log) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(biomeProvider, "biomeProvider");
        Objects.requireNonNull(log, "log");
        List<ResolvedLayer> resolved = plan.layers().stream()
                .map(layer -> new ResolvedLayer(blockData(layer, log), layer.height()))
                .toList();
        return new FlatChunkGenerator(resolved, biomeProvider);
    }

    private static BlockData blockData(FlatLayer layer, Logger log) {
        Material material = Material.matchMaterial(layer.block().namespacedValue());
        if (material == null) {
            log.warn("unknown block {}, using stone", layer.block().value());
            material = Material.STONE;
        }
        return material.createBlockData();
    }

    /** The resolved, pre-composed bands, exposed package-private for the adapter test. */
    List<ResolvedLayer> resolvedPlan() {
        return plan;
    }

    // Paper always invokes an overridden generate*(); the shouldGenerate* flags only gate the vanilla
    // pass that would otherwise run first. With noise suppressed below, this writes into empty ChunkData.
    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        int top = data.getMaxHeight();
        int y = data.getMinHeight();
        for (ResolvedLayer layer : plan) {
            if (y >= top) {
                return;
            }
            int bandTop = Math.min(y + layer.height(), top);
            data.setRegion(0, y, 0, 16, bandTop, 16, layer.block());
            y = bandTop;
        }
    }

    // Suppress every vanilla generation stage; the flat bands are the only blocks written (in
    // generateNoise above). Paper's four-arg shouldGenerate* overloads delegate to these no-arg forms.

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    // shouldGenerateBedrock has no four-arg overload and is deprecated (since 1.19.2, not for removal);
    // its default already returns false, but we override it explicitly so the flat contract is complete.
    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return biomeProvider;
    }
}
