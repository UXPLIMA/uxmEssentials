package com.uxplima.uxmessentials.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.uxplima.uxmessentials.persistence.playerwarps.JooqPlayerWarpBrowse;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.domain.Page;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCard;
import com.uxplima.uxmessentials.playerwarps.domain.WarpQuery;
import com.uxplima.uxmessentials.playerwarps.domain.WarpSort;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.impl.DSL;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * The browse read-model's performance guard: it proves the {@link JooqPlayerWarpBrowse#page} query stays flat as the
 * table grows. The budget is a browse page query p99 &le; 15 ms off-thread at 100,000 warps; the single indexed
 * {@code LIMIT}+{@code COUNT} pair meets it because the work is bounded by the page window (forty-five cards) and the
 * V71 composite indexes, not by the row count: page 0 over 100k warps touches the same number of rows as over 10k.
 *
 * <p>It seeds a real embedded SQLite database (the default backend) with {@code warps} rows via a batched insert and
 * then measures {@code page(publicBrowse(...))}. It is in the JMH source set, so it never ships in the jar and never
 * runs in {@code check}; the perf-regression CI job runs it separately and diffs the result against the baseline.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class PlayerWarpBrowseBenchmark {

    @Param({"10000", "100000"})
    private int warps;

    private Path dataFolder;
    private Persistence persistence;
    private JooqPlayerWarpBrowse browse;
    private UUID viewer;

    @Setup(Level.Trial)
    public void setUp() throws IOException {
        dataFolder = Files.createTempDirectory("pwarp-browse-bench");
        persistence = Persistence.open(new BenchConfig(), dataFolder, List.of("db/migration"), new SilentLogger());
        viewer = UUID.randomUUID();
        persistence.dsl().transaction(configuration -> seed(DSL.using(configuration), warps));
        browse = new JooqPlayerWarpBrowse(persistence.dsl(), Clock.systemUTC());
    }

    @Benchmark
    public Page<WarpCard> firstPageOfAPopularSort() {
        return browse.page(WarpQuery.publicBrowse(viewer, WarpSort.VISITS, 0, 45));
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        persistence.close();
        try (var paths = Files.walk(dataFolder)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(PlayerWarpBrowseBenchmark::deleteQuietly);
        }
    }

    private static void seed(DSLContext dsl, int count) {
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        List<Query> batch = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            batch.add(row(dsl, i, owner, world));
            if (batch.size() == 2_000) {
                dsl.batch(batch).execute();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            dsl.batch(batch).execute();
        }
    }

    private static Query row(DSLContext dsl, int i, UUID owner, UUID world) {
        return dsl.insertInto(PLAYER_WARPS)
                .set(PLAYER_WARPS.ID, (long) i)
                .set(PLAYER_WARPS.NAME, "warp-" + i)
                .set(PLAYER_WARPS.OWNER, owner.toString())
                .set(PLAYER_WARPS.OWNER_NAME, "Owner")
                .set(PLAYER_WARPS.WORLD, world.toString())
                .set(PLAYER_WARPS.WORLD_NAME, "world")
                .set(PLAYER_WARPS.X, (double) (i % 1_000))
                .set(PLAYER_WARPS.Y, 64.0)
                .set(PLAYER_WARPS.Z, (double) (i % 500))
                .set(PLAYER_WARPS.YAW, 0f)
                .set(PLAYER_WARPS.PITCH, 0f)
                .set(PLAYER_WARPS.ACCESS, WarpAccess.PUBLIC.name())
                .set(PLAYER_WARPS.STATUS, WarpStatus.ACTIVE.name())
                .set(PLAYER_WARPS.VISIT_COUNT, (long) (i % 97))
                .set(PLAYER_WARPS.RANDOM_SORT, i * 2_654_435_761L)
                .set(PLAYER_WARPS.CREATED_AT, (long) i)
                .set(PLAYER_WARPS.UPDATED_AT, (long) i);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A leftover temp file after a benchmark run is harmless; the OS reclaims the temp dir.
        }
    }

    /** The embedded SQLite backend with every default: no network coordinates. */
    private record BenchConfig() implements ConfigStore {
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

    private static final class SilentLogger implements Logger {
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
