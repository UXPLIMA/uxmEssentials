package com.uxplima.uxmessentials.persistence.teleport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqRtpPoolRepository} against the default embedded SQLite backend with the Flyway
 * V1-V69 migrations applied. It proves the per-world save/load round-trip (biome included, newest validation first),
 * the per-world {@code count}, the age-based {@code deleteStale} sweep, and the per-world cap that bounds the pool's
 * growth by evicting the oldest-validated rows.
 */
class JooqRtpPoolRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final WorldRef NETHER = new WorldRef(UUID.randomUUID(), "world_nether");
    private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private Persistence persistence;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savedColumnsRoundTripPerWorldNewestFirst() {
        JooqRtpPoolRepository repo = repo(100);
        repo.save(
                WORLD,
                List.of(
                        new RtpColumn(WORLD, 100, 200, BiomeName.of("plains"), NOW.minusSeconds(60)),
                        new RtpColumn(WORLD, 300, 400, BiomeName.of("desert"), NOW)));

        List<RtpColumn> loaded = repo.load(WORLD, 10);

        assertThat(loaded).hasSize(2);
        // Newest validation first.
        assertThat(loaded.get(0).x()).isEqualTo(300);
        assertThat(loaded.get(0).biomeName()).contains(BiomeName.of("desert"));
        assertThat(loaded.get(1).x()).isEqualTo(100);
        assertThat(loaded.get(0).world().uid()).isEqualTo(WORLD.uid());
    }

    @Test
    void columnsAreIsolatedPerWorldAndCounted() {
        JooqRtpPoolRepository repo = repo(100);
        repo.save(WORLD, List.of(new RtpColumn(WORLD, 1, 1, NOW), new RtpColumn(WORLD, 2, 2, NOW)));
        repo.save(NETHER, List.of(new RtpColumn(NETHER, 9, 9, NOW)));

        assertThat(repo.count(WORLD)).isEqualTo(2);
        assertThat(repo.count(NETHER)).isEqualTo(1);
        assertThat(repo.load(NETHER, 10)).extracting(RtpColumn::x).containsExactly(9);
    }

    @Test
    void loadByBiomeReturnsOnlyThatBiomeNewestFirst() {
        JooqRtpPoolRepository repo = repo(100);
        repo.save(
                WORLD,
                List.of(
                        new RtpColumn(WORLD, 100, 100, BiomeName.of("plains"), NOW.minusSeconds(90)),
                        new RtpColumn(WORLD, 200, 200, BiomeName.of("desert"), NOW.minusSeconds(60)),
                        new RtpColumn(WORLD, 300, 300, BiomeName.of("desert"), NOW.minusSeconds(30)),
                        new RtpColumn(WORLD, 400, 400, NOW))); // no recorded biome: never matches

        List<RtpColumn> desert = repo.loadByBiome(WORLD, BiomeName.of("desert"), 10);

        assertThat(desert).extracting(RtpColumn::x).containsExactly(300, 200); // only desert, newest first
        assertThat(desert).allSatisfy(column -> assertThat(column.biomeName()).contains(BiomeName.of("desert")));
        assertThat(repo.loadByBiome(WORLD, BiomeName.of("jungle"), 10)).isEmpty();
    }

    @Test
    void aNullBiomeRoundTripsAsAbsent() {
        JooqRtpPoolRepository repo = repo(100);
        repo.save(WORLD, List.of(new RtpColumn(WORLD, 5, 5, NOW)));

        assertThat(repo.load(WORLD, 10).get(0).biomeName()).isEmpty();
    }

    @Test
    void deleteStaleRemovesColumnsOlderThanTheThreshold() {
        JooqRtpPoolRepository repo = repo(100);
        repo.save(
                WORLD,
                List.of(
                        new RtpColumn(WORLD, 1, 1, NOW.minus(Duration.ofHours(2))),
                        new RtpColumn(WORLD, 2, 2, NOW.minusSeconds(30))));

        int removed = repo.deleteStale(Duration.ofHours(1));

        assertThat(removed).isEqualTo(1);
        assertThat(repo.load(WORLD, 10)).extracting(RtpColumn::x).containsExactly(2);
    }

    @Test
    void theCapBoundsGrowthByEvictingTheOldestValidated() {
        JooqRtpPoolRepository repo = repo(3);
        repo.save(
                WORLD,
                List.of(
                        new RtpColumn(WORLD, 1, 1, NOW.minusSeconds(50)),
                        new RtpColumn(WORLD, 2, 2, NOW.minusSeconds(40)),
                        new RtpColumn(WORLD, 3, 3, NOW.minusSeconds(30))));
        repo.save(
                WORLD,
                List.of(
                        new RtpColumn(WORLD, 4, 4, NOW.minusSeconds(20)),
                        new RtpColumn(WORLD, 5, 5, NOW.minusSeconds(10))));

        assertThat(repo.count(WORLD)).isEqualTo(3);
        // The three newest-validated survive; the two oldest were evicted.
        assertThat(repo.load(WORLD, 10)).extracting(RtpColumn::x).containsExactly(5, 4, 3);
    }

    private JooqRtpPoolRepository repo(int maxPerWorld) {
        return new JooqRtpPoolRepository(persistence.dsl(), maxPerWorld, clock);
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
