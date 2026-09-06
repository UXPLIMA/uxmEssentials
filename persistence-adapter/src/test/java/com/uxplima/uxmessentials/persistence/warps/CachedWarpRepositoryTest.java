package com.uxplima.uxmessentials.persistence.warps;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.Test;

/**
 * Pins the in-memory-authoritative behaviour of {@link CachedWarpRepository} against a counting fake delegate.
 * The {@code /warp} command handlers call {@code exists}/{@code find}/{@code all} on the command (tick/region)
 * thread to resolve a warp name, gate an edit, or complete a tab suggestion; those reads must be served from the
 * warm-loaded in-memory set, never a synchronous SQLite query on that thread. So after a single warm load (which
 * the wiring triggers on enable), any number of command-style {@code exists}/{@code find}/{@code all} reads hit
 * the delegate zero further times, while a {@code save} makes the new warp immediately findable and a
 * {@code delete} immediately absent. Both write-through at the delegate and reflected in memory with no extra
 * delegate read. {@code rate}/{@code averageRating} are not part of the cached set and pass straight through.
 */
class CachedWarpRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final WarpName SPAWN = WarpName.of("spawn");
    private static final WarpName SHOP = WarpName.of("shop");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "owner");

    private static Warp warp(WarpName name) {
        return Warp.create(name, Position.of(WORLD, 1, 64, 1), OWNER, Instant.ofEpochMilli(1_000));
    }

    @Test
    void repeatedExistsFindAndAllReadsHitTheDelegateOnceThenServeFromMemory() {
        CountingRepository delegate = new CountingRepository();
        delegate.store(warp(SPAWN));
        CachedWarpRepository cached = new CachedWarpRepository(delegate);

        cached.all(); // the wiring's warm load on enable

        for (int i = 0; i < 100; i++) {
            assertThat(cached.exists(SPAWN)).isTrue();
            assertThat(cached.exists(SHOP)).isFalse();
            assertThat(cached.find(SPAWN)).isPresent();
            assertThat(cached.find(SHOP)).isEmpty();
            assertThat(cached.all()).hasSize(1);
        }

        // Only the single warm load read the delegate; every command-style lookup served from memory.
        assertThat(delegate.allReads.get()).isEqualTo(1);
    }

    @Test
    void aSavedWarpIsImmediatelyFindableWithoutAFurtherDelegateRead() {
        CountingRepository delegate = new CountingRepository();
        CachedWarpRepository cached = new CachedWarpRepository(delegate);

        cached.all(); // warm load over an empty set
        assertThat(cached.exists(SHOP)).isFalse();

        cached.save(warp(SHOP)); // write-through at the delegate, reflected in the in-memory set

        assertThat(cached.exists(SHOP)).isTrue();
        assertThat(cached.find(SHOP)).isPresent();
        assertThat(cached.all()).hasSize(1);
        assertThat(delegate.allReads.get()).isEqualTo(1); // still just the one warm load
        assertThat(delegate.saves.get()).isEqualTo(1);
    }

    @Test
    void aDeletedWarpIsImmediatelyAbsentWithoutAFurtherDelegateRead() {
        CountingRepository delegate = new CountingRepository();
        delegate.store(warp(SPAWN));
        CachedWarpRepository cached = new CachedWarpRepository(delegate);

        cached.all(); // warm load
        assertThat(cached.exists(SPAWN)).isTrue();

        cached.delete(SPAWN); // write-through at the delegate, removed from the in-memory set

        assertThat(cached.exists(SPAWN)).isFalse();
        assertThat(cached.find(SPAWN)).isEmpty();
        assertThat(cached.all()).isEmpty();
        assertThat(delegate.allReads.get()).isEqualTo(1); // still just the one warm load
        assertThat(delegate.deletes.get()).isEqualTo(1);
    }

    @Test
    void firstReadBeforeAnyWarmLoadLoadsOnceThenServesFromMemory() {
        CountingRepository delegate = new CountingRepository();
        delegate.store(warp(SPAWN));
        CachedWarpRepository cached = new CachedWarpRepository(delegate);

        // Even without an explicit warm load, the first lookup loads the set, then every later read is served
        // from memory: a later miss never re-queries the delegate.
        assertThat(cached.exists(SPAWN)).isTrue();
        for (int i = 0; i < 50; i++) {
            assertThat(cached.exists(SHOP)).isFalse();
            assertThat(cached.find(SPAWN)).isPresent();
        }

        assertThat(delegate.allReads.get()).isEqualTo(1);
    }

    @Test
    void invalidateAllForcesTheNextReadToReloadFromTheDelegate() {
        CountingRepository delegate = new CountingRepository();
        delegate.store(warp(SPAWN));
        CachedWarpRepository cached = new CachedWarpRepository(delegate);

        cached.all(); // warm load
        delegate.store(warp(SHOP)); // a peer wrote a new warp straight to the shared database
        assertThat(cached.exists(SHOP)).isFalse(); // not yet visible. Still serving the loaded set

        cached.invalidateAll(); // a cross-server change drops the loaded set

        assertThat(cached.exists(SHOP)).isTrue(); // the next read reloaded and now sees the peer's warp
        assertThat(delegate.allReads.get()).isEqualTo(2);
    }

    @Test
    void rateAndAverageRatingPassStraightThroughToTheDelegate() {
        CountingRepository delegate = new CountingRepository();
        delegate.store(warp(SPAWN));
        CachedWarpRepository cached = new CachedWarpRepository(delegate);

        cached.rate(SPAWN, OWNER.uuid(), 4.0);
        assertThat(cached.averageRating(SPAWN)).isEqualTo(4.0);
        assertThat(delegate.rates.get()).isEqualTo(1);
    }

    @Test
    void concurrentSavesFromManyThreadsAllRemainInTheAuthoritativeIndex() throws InterruptedException {
        CountingRepository delegate = new CountingRepository();
        CachedWarpRepository cached = new CachedWarpRepository(delegate);
        cached.all(); // warm load over an empty set

        int threads = 16;
        List<WarpName> names = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            names.add(WarpName.of("warp" + i));
        }

        // Each thread saves a distinct warp at the same moment. A racy read-modify-set would lose all but the
        // last writer's entry from the in-memory index (the index is authoritative, so a lost entry reads as
        // absent); the CAS retry loop must compose every save so all of them survive.
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (WarpName name : names) {
            Thread t = new Thread(() -> {
                awaitQuietly(start);
                cached.save(warp(name));
                done.countDown();
            });
            t.start();
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        for (WarpName name : names) {
            assertThat(cached.exists(name)).as("warp %s present in index", name).isTrue();
        }
        assertThat(cached.all()).hasSize(threads);
    }

    @Test
    void concurrentSavesAndDeletesComposeWithoutLosingUntouchedEntries() throws InterruptedException {
        CountingRepository delegate = new CountingRepository();
        CachedWarpRepository cached = new CachedWarpRepository(delegate);

        int count = 32;
        List<WarpName> survivors = new ArrayList<>();
        List<WarpName> doomed = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WarpName survivor = WarpName.of("keep" + i);
            WarpName victim = WarpName.of("drop" + i);
            survivors.add(survivor);
            doomed.add(victim);
            cached.save(warp(survivor));
            cached.save(warp(victim));
        }

        // Delete every "drop" warp concurrently. A racy publish would let one delete clobber another's removal or
        // resurrect an unrelated "keep" entry; the CAS loop must leave exactly the survivors behind.
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(doomed.size());
        for (WarpName victim : doomed) {
            Thread t = new Thread(() -> {
                awaitQuietly(start);
                cached.delete(victim);
                done.countDown();
            });
            t.start();
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        for (WarpName survivor : survivors) {
            assertThat(cached.exists(survivor)).as("survivor %s kept", survivor).isTrue();
        }
        for (WarpName victim : doomed) {
            assertThat(cached.exists(victim)).as("victim %s gone", victim).isFalse();
        }
        assertThat(cached.all()).hasSize(survivors.size());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to start", e);
        }
    }

    /** A fake delegate that counts {@code all}/{@code save}/{@code delete}/{@code rate} calls and holds warps. */
    private static final class CountingRepository implements WarpRepository {

        private final Map<String, Warp> stored = new ConcurrentHashMap<>();
        private final AtomicInteger allReads = new AtomicInteger();
        private final AtomicInteger saves = new AtomicInteger();
        private final AtomicInteger deletes = new AtomicInteger();
        private final AtomicInteger rates = new AtomicInteger();

        void store(Warp warp) {
            stored.put(warp.name().value(), warp);
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(stored.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            allReads.incrementAndGet();
            return new ArrayList<>(stored.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return stored.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            saves.incrementAndGet();
            store(Objects.requireNonNull(warp, "warp"));
        }

        @Override
        public void delete(WarpName name) {
            deletes.incrementAndGet();
            stored.remove(name.value());
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {
            rates.incrementAndGet();
        }

        @Override
        public double averageRating(WarpName name) {
            return 4.0;
        }
    }
}
