package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.bukkit.Chunk;
import org.bukkit.event.world.ChunkLoadEvent;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.BiomeHotspots;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.HotspotChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * The passive rare-biome listener reading an already-loaded chunk's biome on {@link ChunkLoadEvent}: a chunk is
 * recorded once, a second load of the same chunk neither duplicates nor re-scans it, and the seen-set is bounded so
 * a chunk beyond the window is scanned again rather than remembered forever.
 */
class BiomeHotspotListenerTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aChunkLoadRecordsItsBiomeOnce() {
        RecordingHotspots hotspots = new RecordingHotspots();
        BiomeHotspotListener listener = new BiomeHotspotListener(hotspots, 1_000);
        Chunk chunk = world.getChunkAt(3, 5);

        listener.onChunkLoad(new ChunkLoadEvent(chunk, true));

        // A flat mock world is one biome across the chunk, so exactly one hotspot is recorded, at the chunk's coords.
        assertThat(hotspots.records).hasSize(1);
        assertThat(hotspots.records.get(0).chunkX()).isEqualTo(3);
        assertThat(hotspots.records.get(0).chunkZ()).isEqualTo(5);
        assertThat(hotspots.records.get(0).world()).isEqualTo(world.getUID());
    }

    @Test
    void asecondLoadOfTheSameChunkDoesNotReScan() {
        RecordingHotspots hotspots = new RecordingHotspots();
        BiomeHotspotListener listener = new BiomeHotspotListener(hotspots, 1_000);
        Chunk chunk = world.getChunkAt(3, 5);

        listener.onChunkLoad(new ChunkLoadEvent(chunk, true));
        listener.onChunkLoad(new ChunkLoadEvent(chunk, false));

        assertThat(hotspots.records).hasSize(1); // deduped by chunk key, no duplicate, no re-scan
    }

    @Test
    void theSeenSetIsBoundedSoAnOutOfWindowChunkIsScannedAgain() {
        RecordingHotspots hotspots = new RecordingHotspots();
        // A window of one chunk: loading a second chunk resets the window, so the first is no longer remembered.
        BiomeHotspotListener listener = new BiomeHotspotListener(hotspots, 1);
        Chunk first = world.getChunkAt(0, 0);
        Chunk second = world.getChunkAt(9, 9);

        listener.onChunkLoad(new ChunkLoadEvent(first, true));
        listener.onChunkLoad(new ChunkLoadEvent(second, true));
        listener.onChunkLoad(new ChunkLoadEvent(first, false));

        // first(0,0) scanned twice: the bounded window forgot it after second(9,9), rather than growing without limit.
        long firstChunkRecords = hotspots.records.stream()
                .filter(record -> record.chunkX() == 0 && record.chunkZ() == 0)
                .count();
        assertThat(firstChunkRecords).isEqualTo(2);
    }

    private record Recorded(UUID world, BiomeName biome, int chunkX, int chunkZ) {}

    /** A {@link BiomeHotspots} that records every call so the test can assert how often a chunk was scanned. */
    private static final class RecordingHotspots implements BiomeHotspots {
        private final List<Recorded> records = new ArrayList<>();

        @Override
        public void record(WorldRef world, BiomeName biome, int chunkX, int chunkZ) {
            records.add(new Recorded(world.uid(), biome, chunkX, chunkZ));
        }

        @Override
        public Optional<HotspotChunk> sample(WorldRef world, BiomeName biome, RandomGenerator random) {
            return Optional.empty();
        }
    }
}
