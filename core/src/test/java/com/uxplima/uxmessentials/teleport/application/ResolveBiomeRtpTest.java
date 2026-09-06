package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.BiomeCatalog;
import com.uxplima.uxmessentials.teleport.application.port.ChunkAccess;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.RtpAreaSource;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;
import com.uxplima.uxmessentials.teleport.domain.SafeCandidate;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchPolicy;
import com.uxplima.uxmessentials.teleport.domain.SearchBudget;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code /rtp biome} use case, driven with fakes and a synchronous scheduler so the whole async chain resolves
 * in-test: an unknown biome is reported and never searches; a biome served from the per-biome pool slice teleports
 * the player; a biome found only by the live search teleports too; and a biome nothing turns up reports the clear
 * not-found message.
 */
class ResolveBiomeRtpTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final BiomeName DESERT = BiomeName.of("desert");
    private static final SafeSearchArea AREA = new SafeSearchArea(WORLD, 0, 0, 0, 10_000, 10_000);
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Explorer");

    private RecordingExecutor executor;
    private CapturingSink sink;
    private RecordingSlice slice;

    @BeforeEach
    void setUp() {
        executor = new RecordingExecutor();
        sink = new CapturingSink();
        slice = new RecordingSlice();
    }

    @Test
    void anUnknownBiomeIsReportedAndNothingSearches() {
        ResolveBiomeRtp resolve = resolve(new EmptyChunkAccess(), slice);

        Result<Unit, TeleportError> result = resolve.targeted(WHO, WORLD, "not_a_biome");

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.RTP_BIOME_UNKNOWN);
        assertThat(sink.delivered).contains("teleport.rtp.biome-unknown");
        assertThat(executor.hops).isZero();
        assertThat(slice.calls).isZero(); // never even reached the search
    }

    @Test
    void aBiomeServedFromThePoolSliceTeleportsThePlayer() {
        slice.columns = List.of(new RtpColumn(WORLD, 100, 200, DESERT, Instant.EPOCH));
        ResolveBiomeRtp resolve = resolve(new MatchingChunkAccess(DESERT), slice);

        Result<Unit, TeleportError> result = resolve.targeted(WHO, WORLD, "desert");

        assertThat(result.isOk()).isTrue();
        assertThat(slice.calls).isEqualTo(1);
        assertThat(slice.lastBiome).isEqualTo(DESERT); // the load is biome-filtered
        assertThat(executor.hops).isEqualTo(1);
        assertThat(sink.delivered).contains("teleport.rtp.searching");
    }

    @Test
    void aBiomeFoundOnlyByTheLiveSearchTeleportsThePlayer() {
        slice.columns = List.of(); // nothing persisted for this biome, fall through to the live search
        ResolveBiomeRtp resolve = resolve(new MatchingChunkAccess(DESERT), slice);

        Result<Unit, TeleportError> result = resolve.targeted(WHO, WORLD, "desert");

        assertThat(result.isOk()).isTrue();
        assertThat(executor.hops).isEqualTo(1);
    }

    @Test
    void aBiomeThatCannotBeFoundReportsNotFound() {
        slice.columns = List.of();
        ResolveBiomeRtp resolve = resolve(new EmptyChunkAccess(), slice);

        Result<Unit, TeleportError> result = resolve.targeted(WHO, WORLD, "desert");

        assertThat(result.isOk()).isTrue(); // the request was accepted; the async search then failed to place
        assertThat(executor.hops).isZero();
        assertThat(sink.delivered).contains("teleport.rtp.biome-not-found");
    }

    private ResolveBiomeRtp resolve(ChunkAccess chunkAccess, BiomePoolSlice poolSlice) {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        Scheduler scheduler = new InlineScheduler();
        Logger log = new NoopLogger();
        Notifier notifier = new Notifier(new KeyMessages(), sink);
        AsyncSafeLocationFinder finder = new AsyncSafeLocationFinder(chunkAccess, SafeSearchPolicy.permissive(), clock);
        BudgetedSafeSearch budgeted = new BudgetedSafeSearch(finder, scheduler, clock, Duration.ofMillis(1));
        BiomeTargetedSearch search = new BiomeTargetedSearch(
                poolSlice,
                finder,
                budgeted,
                () -> new SearchBudget(5, 5, 1_000),
                scheduler,
                log,
                Duration.ofMillis(1),
                8);
        TeleportEngine engine = new TeleportEngine(
                new NoopCooldowns(),
                new ImmediateWarmups(),
                executor,
                notifier,
                new NoopEvents(),
                new TeleportSettings(new EmptyConfig()),
                JailGate.NEVER,
                TeleportFee.FREE,
                com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace.NONE);
        return new ResolveBiomeRtp(
                new FakeCatalog(),
                new FixedAreaSource(),
                new AnyWorldLookup(),
                new TeleportSettings(new EmptyConfig()),
                engine,
                search,
                notifier,
                scheduler,
                new RtpRadiusTier(new NoTierPermissions()));
    }

    /** A permissions fake with no radius tier: {@code resolveQuota} echoes the config default, so the area is unchanged. */
    private static final class NoTierPermissions
            implements com.uxplima.uxmessentials.shared.application.port.Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who,
                QuotaFamily family,
                @org.jspecify.annotations.Nullable WorldRef world,
                long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    /** Resolves only {@code desert}; every other key is unknown. */
    private static final class FakeCatalog implements BiomeCatalog {
        @Override
        public Optional<BiomeName> resolve(String rawKey) {
            return "desert".equals(rawKey) ? Optional.of(DESERT) : Optional.empty();
        }

        @Override
        public List<String> keys() {
            return List.of("desert", "plains");
        }
    }

    private static final class FixedAreaSource implements RtpAreaSource {
        @Override
        public Optional<SafeSearchArea> areaFor(WorldRef world) {
            return Optional.of(AREA);
        }
    }

    /** A pool slice that returns a preset column list and records the biome it was queried for. */
    private static final class RecordingSlice implements BiomePoolSlice {
        private List<RtpColumn> columns = List.of();
        private int calls;
        private @org.jspecify.annotations.Nullable BiomeName lastBiome;

        @Override
        public List<RtpColumn> load(WorldRef world, BiomeName biome, int limit) {
            calls++;
            lastBiome = biome;
            return columns;
        }
    }

    /** A chunk access that always returns a safe candidate in {@code biome} at the probed column. */
    private static final class MatchingChunkAccess implements ChunkAccess {
        private final BiomeName biome;

        MatchingChunkAccess(BiomeName biome) {
            this.biome = biome;
        }

        @Override
        public CompletableFuture<Optional<SafeCandidate>> probe(SafeSearchArea area, int blockX, int blockZ) {
            Position position = new Position(area.world(), blockX + 0.5, 70.0, blockZ + 0.5, 0f, 0f);
            return CompletableFuture.completedFuture(Optional.of(new SafeCandidate(position, biome, true, false)));
        }
    }

    /** A chunk access that never yields a candidate: every probe misses. */
    private static final class EmptyChunkAccess implements ChunkAccess {
        @Override
        public CompletableFuture<Optional<SafeCandidate>> probe(SafeSearchArea area, int blockX, int blockZ) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /** Runs every scheduled unit of work inline so the async chain resolves within the test call. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    private static final class RecordingExecutor implements TeleportExecutor {
        private int hops;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            hops++;
        }
    }

    private static final class AnyWorldLookup implements WorldLookup {
        @Override
        public Optional<WorldRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<WorldRef> findByUid(UUID uid) {
            return Optional.empty();
        }
    }

    private static final class NoopCooldowns implements Cooldowns {
        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class ImmediateWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new CompletedWarmup(who);
        }
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        private final List<String> delivered = new java.util.ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
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

    private record EmptyConfig() implements com.uxplima.uxmessentials.shared.application.port.ConfigStore {
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

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return fallback;
        }
    }
}
