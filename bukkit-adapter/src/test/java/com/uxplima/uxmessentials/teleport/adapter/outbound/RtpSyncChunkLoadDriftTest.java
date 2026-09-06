package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethodWhere;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Set;

import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/**
 * The RTP async-chunk invariant guard (CLAUDE.md §1 Folia-ready / §3 no BukkitScheduler kin,
 * docs/02-concurrency.md, the RTP redesign spec §1/§6 and ledger rule "no synchronous chunk load on a tick
 * thread, EVER").
 *
 * <p><strong>The bug this freezes out.</strong> Before the P0 rewrite the RTP safe-search read each candidate's
 * chunk synchronously, {@code world.getHighestBlockYAt(...)} / {@code world.getBlockAt(...)} /
 * {@code world.getBiome(...)}, from inside a {@code scheduler.onRegion(...)} lambda, i.e. on a tick/region
 * thread. Those {@link World} methods force-<em>generate</em> the chunk on the calling thread if it is not
 * already resident, so a refill that probed up to thirty-two far candidates generated thirty-two chunks inline
 * and dropped the server to about five TPS; the probed chunks were never unloaded, leaking heap and region-file
 * bloat. The rewrite moved every probe onto {@code World#getChunkAtAsync} plus off-thread
 * {@link ChunkSnapshot} reads. This guard makes sure the fast path can never be reintroduced silently.
 *
 * <p><strong>What it scans.</strong> An ArchUnit (bytecode) fence. The robust house pattern, matching
 * {@code ArchitectureTest}'s inventory fence rather than a brittle line regex, over the RTP safe-search
 * outbound adapters ({@code ..teleport.adapter.outbound..}, where the {@code ChunkAccess} probe impl
 * {@link BukkitChunkAccess} and the pre-warm / queue / pool search helpers live). It fails if any of those
 * classes <em>calls</em> a synchronous, chunk-loading method on {@code org.bukkit.World} /
 * {@code org.bukkit.RegionAccessor}: {@code getHighestBlockYAt}, {@code getHighestBlockAt}, {@code getBlockAt},
 * {@code getBiome}, {@code getChunkAt} (the non-{@code Async} overloads), or {@code loadChunk}. The match keys on
 * the called method's declaring type <em>and</em> name, so it is precise about the receiver.
 *
 * <p><strong>What stays allowed.</strong> {@code World#getChunkAtAsync} (the off-main generate),
 * {@code World#isChunkLoaded} and {@code World#unloadChunkRequest} (cheap, non-loading lifecycle calls), and
 * every read off a {@link ChunkSnapshot} copy, {@code getBiome} / {@code getBlockType} on the <em>snapshot</em>
 * are off-thread-safe because they read a detached copy, and their declaring type is {@code ChunkSnapshot},
 * not {@code World}, so they are structurally outside the forbidden-owner set.
 *
 * <p><strong>How it is scoped so it does not false-positive.</strong> Only the outbound safe-search adapters are
 * in scope. Two teleport classes legitimately read chunk data through the synchronous {@link World} methods and
 * are deliberately <em>outside</em> this scope because neither is the RTP probe path and neither forces a far
 * generate:
 *
 * <ul>
 *   <li>{@code teleport.adapter.inbound.listener.BiomeHotspotListener} samples biomes on a
 *       {@code ChunkLoadEvent}. The chunk is already resident and the reads stay within its bounds, on the
 *       chunk's own owning region thread.</li>
 *   <li>{@code teleport.adapter.inbound.command.VerticalCommand} ({@code /top}, {@code /vertical}) reads the
 *       player's own column, already loaded around the player, on the command (global) thread.</li>
 * </ul>
 *
 * <p>The {@code worlds} context, which legitimately loads and generates chunks, is untouched for the same
 * reason: it is not in {@code teleport.adapter.outbound}.
 *
 * <p><strong>Proof of teeth.</strong> {@link LegacySyncProbeFixture} reproduces the exact pre-P0 pattern (a
 * class in the guarded package calling the synchronous {@link World} probes); the negative test evaluates the
 * live rule against it and asserts a violation, so a regression that adds a synchronous chunk load to a probe
 * adapter would fail the build. {@link AsyncProbeFixture} mirrors the current correct shape
 * ({@code getChunkAtAsync} + snapshot reads + the lifecycle calls) and the positive test asserts the rule does
 * <em>not</em> flag it, so the guard cannot drift into false positives on the async path.
 */
class RtpSyncChunkLoadDriftTest {

    /** The RTP safe-search probe / search adapters: the one place a stray synchronous chunk load would crater TPS. */
    private static final String RTP_SAFE_SEARCH_PACKAGE = "..teleport.adapter.outbound..";

    /**
     * The declaring types whose chunk reads force-load the chunk on the calling thread. {@code ChunkSnapshot} is
     * intentionally absent: reads off a snapshot copy are the sanctioned off-thread path.
     */
    private static final Set<String> SYNC_CHUNK_LOADING_OWNERS =
            Set.of("org.bukkit.World", "org.bukkit.RegionAccessor");

    /**
     * The synchronous, chunk-loading methods on those types. {@code getChunkAtAsync} is deliberately excluded (a
     * different method name); {@code isChunkLoaded} / {@code unloadChunkRequest} are non-loading and also excluded.
     */
    private static final Set<String> SYNC_CHUNK_LOADING_METHODS =
            Set.of("getHighestBlockYAt", "getHighestBlockAt", "getBlockAt", "getBiome", "getChunkAt", "loadChunk");

    private static final String INVARIANT = "the RTP safe-search adapters read chunk data only off the main thread "
            + "(World#getChunkAtAsync + ChunkSnapshot copies). A synchronous World#getHighestBlockYAt / getBlockAt / "
            + "getBiome / getChunkAt / loadChunk force-generates a far chunk on the calling tick/region thread, the "
            + "exact bug that dropped RTP to ~5 TPS and leaked chunks. Load the chunk with getChunkAtAsync and read "
            + "from the snapshot instead.";

    @Test
    void rtpSafeSearchAdaptersNeverSynchronouslyLoadAChunk() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.uxplima.uxmessentials.teleport");
        assertThatCode(() -> rule().check(production))
                .as("current RTP safe-search adapters must stay free of synchronous chunk loads")
                .doesNotThrowAnyException();
    }

    @Test
    void guardTripsWhenAProbeAdapterSynchronouslyLoadsAChunk() {
        // Prove the fence has teeth: the pre-P0 pattern, dropped into a guarded-package class, must be caught.
        JavaClasses fixture = new ClassFileImporter().importClasses(LegacySyncProbeFixture.class);
        EvaluationResult result = rule().evaluate(fixture);
        assertThat(result.hasViolation())
                .as("the guard must catch a synchronous chunk load reintroduced into an RTP probe adapter")
                .isTrue();
        assertThat(result.getFailureReport().toString()).contains(LegacySyncProbeFixture.class.getSimpleName());
    }

    @Test
    void guardAllowsTheAsyncProbeShape() {
        // The current correct shape, getChunkAtAsync + ChunkSnapshot reads + isChunkLoaded/unloadChunkRequest,
        // must never be flagged, or the guard would false-positive the async path it is meant to protect.
        JavaClasses fixture = new ClassFileImporter().importClasses(AsyncProbeFixture.class);
        assertThat(rule().evaluate(fixture).hasViolation())
                .as("getChunkAtAsync, ChunkSnapshot reads, and the non-loading lifecycle calls are the allowed path")
                .isFalse();
    }

    private static ArchRule rule() {
        return noClasses()
                .that()
                .resideInAPackage(RTP_SAFE_SEARCH_PACKAGE)
                .should(callMethodWhere(synchronousChunkLoad()))
                .because(INVARIANT)
                .allowEmptyShould(true);
    }

    /**
     * Matches a method call that synchronously loads a chunk: one whose declaring type is {@code org.bukkit.World}
     * or {@code org.bukkit.RegionAccessor} and whose name is a chunk-loading accessor. Keying on the declaring type
     * as well as the name is what keeps {@code ChunkSnapshot#getBiome} (an off-thread snapshot read) and
     * {@code World#getChunkAtAsync} (a different name) out of the match.
     */
    private static DescribedPredicate<JavaMethodCall> synchronousChunkLoad() {
        return DescribedPredicate.describe(
                "call a synchronous chunk-loading World method (getHighestBlockYAt / getHighestBlockAt / getBlockAt "
                        + "/ getBiome / getChunkAt / loadChunk)",
                call -> SYNC_CHUNK_LOADING_OWNERS.contains(call.getTargetOwner().getFullName())
                        && SYNC_CHUNK_LOADING_METHODS.contains(call.getName()));
    }

    /**
     * The pre-P0 synchronous-probe pattern, reproduced in the guarded package so the negative test can prove the
     * fence catches it. Reads chunk data through the {@link World} methods that force-generate a far chunk on the
     * calling thread: exactly what crashed the tick rate. Never executed; ArchUnit inspects the bytecode.
     */
    static final class LegacySyncProbeFixture {

        int probe(World world, int blockX, int blockZ) {
            int groundY = world.getHighestBlockYAt(blockX, blockZ);
            boolean solid = world.getBlockAt(blockX, groundY, blockZ).getType().isSolid();
            int biome = world.getBiome(blockX, groundY, blockZ).hashCode();
            int chunkX = world.getChunkAt(blockX >> 4, blockZ >> 4).getX();
            world.loadChunk(blockX >> 4, blockZ >> 4);
            return groundY + biome + chunkX + (solid ? 1 : 0);
        }
    }

    /**
     * The current correct probe shape, so the positive test can prove the guard leaves the async path alone:
     * {@code getChunkAtAsync} plus reads off a {@link ChunkSnapshot} copy, with only the non-loading lifecycle
     * calls on {@link World}. Never executed; ArchUnit inspects the bytecode.
     */
    static final class AsyncProbeFixture {

        boolean probe(World world, ChunkSnapshot snapshot, int blockX, int blockZ) {
            int cx = blockX >> 4;
            int cz = blockZ >> 4;
            boolean wasLoaded = world.isChunkLoaded(cx, cz);
            var pending = world.getChunkAtAsync(cx, cz, true);
            world.unloadChunkRequest(cx, cz);
            int biome = snapshot.getBiome(blockX & 15, 64, blockZ & 15).hashCode();
            boolean air = snapshot.getBlockType(blockX & 15, 64, blockZ & 15).isAir();
            return wasLoaded && air && biome != 0 && pending != null;
        }
    }
}
