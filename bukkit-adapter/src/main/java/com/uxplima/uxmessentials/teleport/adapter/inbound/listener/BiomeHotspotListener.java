package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.BiomeHotspots;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import org.jspecify.annotations.NullMarked;

/**
 * Passively learns where rare biomes are: every time a chunk loads (as players explore, or worldgen streams terrain),
 * this reads the <em>already-resident</em> chunk's surface biomes once and records them in the {@link BiomeHotspots}
 * registry. Because the chunk is already loaded when {@link ChunkLoadEvent} fires, and the handler runs on the
 * chunk's own owning region thread. The biome read is a free in-memory lookup, never a chunk load, so this adds no
 * generation pressure and honours the RTP redesign's "no synchronous chunk load on a tick thread" invariant.
 *
 * <p>Each chunk is scanned <strong>at most once</strong>: a bounded seen-set dedups by chunk key so a chunk that
 * loads and unloads repeatedly is not re-scanned. The set is capped and reset wholesale when it fills, so it can
 * never grow without limit on a long-lived, heavily-explored server: the registry it feeds is bounded too.
 */
@NullMarked
public final class BiomeHotspotListener implements Listener {

    // Local block offsets sampled within a 16×16 chunk. The four quadrant centres, enough to catch a biome that
    // only touches part of the chunk without reading all 256 columns.
    private static final int[] SAMPLE_OFFSETS = {4, 12};

    private final BiomeHotspots hotspots;
    private final int maxSeenChunks;
    private final Set<ChunkKey> seen = ConcurrentHashMap.newKeySet();

    public BiomeHotspotListener(BiomeHotspots hotspots, int maxSeenChunks) {
        this.hotspots = Objects.requireNonNull(hotspots, "hotspots");
        if (maxSeenChunks < 1) {
            throw new IllegalArgumentException("maxSeenChunks must be >= 1: " + maxSeenChunks);
        }
        this.maxSeenChunks = maxSeenChunks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        World world = event.getWorld();
        if (!markSeen(new ChunkKey(world.getUID(), chunk.getX(), chunk.getZ()))) {
            return; // already scanned this chunk, no duplicate, no re-scan
        }
        recordBiomes(world, chunk.getX(), chunk.getZ());
    }

    /** Record this chunk's key, resetting the bounded window when it fills; true when the chunk is newly seen. */
    private boolean markSeen(ChunkKey key) {
        if (seen.size() >= maxSeenChunks) {
            seen.clear();
        }
        return seen.add(key);
    }

    private void recordBiomes(World world, int chunkX, int chunkZ) {
        WorldRef worldRef = new WorldRef(world.getUID(), world.getName());
        Set<BiomeName> distinct = new HashSet<>();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int dx : SAMPLE_OFFSETS) {
            for (int dz : SAMPLE_OFFSETS) {
                distinct.add(biomeAt(world, baseX + dx, baseZ + dz));
            }
        }
        for (BiomeName biome : distinct) {
            hotspots.record(worldRef, biome, chunkX, chunkZ);
        }
    }

    private static BiomeName biomeAt(World world, int blockX, int blockZ) {
        int y = world.getHighestBlockYAt(blockX, blockZ);
        return BiomeName.of(world.getBiome(blockX, y, blockZ).getKey().getKey());
    }

    /** A chunk's identity for the seen-set: its world and grid coordinates. */
    private record ChunkKey(UUID world, int x, int z) {}
}
