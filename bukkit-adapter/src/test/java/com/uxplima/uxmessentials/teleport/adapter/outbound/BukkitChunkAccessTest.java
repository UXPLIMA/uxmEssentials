package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import com.uxplima.uxmessentials.teleport.application.ClaimAwareProtectedLand;
import com.uxplima.uxmessentials.teleport.application.port.ProtectedLand;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.BlockTypeName;
import com.uxplima.uxmessentials.teleport.domain.SafeCandidate;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Pins {@link BukkitChunkAccess}'s off-thread candidate build, the null-world orphan fix, and the
 * chunk-lifecycle cleanup.
 *
 * <p>MockBukkit v26 does not implement {@code World#getChunkAtAsync}, {@code ChunkSnapshot#getHighestBlockYAt},
 * or {@code World#unloadChunkRequest} (all throw {@code UnimplementedOperationException}), so the async load
 * and the request-unload call are exercised only against a live Paper server. Here the pieces that MockBukkit
 * <em>can</em> drive are pinned directly: the snapshot read (via {@link BukkitChunkAccess#readCandidate} over a
 * real loaded-chunk snapshot), the absent-world completion, and the unload <em>decision</em> (via a mocked
 * {@code World}, verifying the request is made only for a chunk the probe itself loaded).
 */
class BukkitChunkAccessTest {

    private ServerMock server;
    private BukkitChunkAccess access;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // The terrain-read and chunk-lifecycle tests do not care about protection: never-protected keeps the
        // candidate's insideClaim flag false, isolating those assertions from the claim/region seam.
        access = new BukkitChunkAccess(server, new NoOpLogger(), ProtectedLand.NONE);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void probeOverAnAbsentWorldCompletesEmptyRatherThanOrphaning() {
        // The world unloaded (or never existed): the old engine left this future orphaned, hanging the refill
        // loop. It must now complete, empty, at once.
        WorldRef ghost = new WorldRef(UUID.randomUUID(), "ghost");
        SafeSearchArea area = new SafeSearchArea(ghost, 0.0, 0.0, 0.0, 1_000.0, 100_000.0);

        CompletableFuture<Optional<SafeCandidate>> result = access.probe(area, 0, 0);

        assertThat(result).isCompleted();
        assertThat(result.join()).isEmpty();
    }

    @Test
    void readCandidateResolvesTheHighestGroundFromTheSnapshotOffTheLiveChunk() {
        WorldMock world = server.addSimpleWorld("rtp");
        int blockX = 40;
        int blockZ = 40;
        // Drop a solid block well above the flat surface so the top-down scan has an unambiguous highest ground.
        int groundY = world.getHighestBlockYAt(blockX, blockZ) + 20;
        world.getBlockAt(blockX, groundY, blockZ).setType(Material.STONE);
        ChunkSnapshot snapshot = world.getChunkAt(blockX >> 4, blockZ >> 4).getChunkSnapshot(true, true, false);
        WorldRef ref = new WorldRef(world.getUID(), world.getName());

        SafeCandidate candidate =
                access.readCandidate(ref, snapshot, world.getMinHeight(), world.getMaxHeight(), blockX, blockZ);

        assertThat(candidate.position().x()).isEqualTo(blockX + 0.5);
        assertThat(candidate.position().z()).isEqualTo(blockZ + 0.5);
        assertThat(candidate.y()).isEqualTo(groundY + 1.0);
        assertThat(candidate.standingSafe()).isTrue();
        assertThat(candidate.landing()).contains(BlockTypeName.of("stone"));
        assertThat(candidate.biome())
                .isEqualTo(BiomeName.of(
                        world.getBiome(blockX, groundY, blockZ).getKey().getKey()));
    }

    @Test
    void readCandidateOverALiquidSurfaceIsNotStandingSafe() {
        WorldMock world = server.addSimpleWorld("rtp");
        int blockX = 8;
        int blockZ = 8;
        // A water surface as the highest block (an ocean top): the column has no solid, non-liquid ground.
        int surfaceY = world.getHighestBlockYAt(blockX, blockZ) + 20;
        world.getBlockAt(blockX, surfaceY, blockZ).setType(Material.WATER);
        ChunkSnapshot snapshot = world.getChunkAt(blockX >> 4, blockZ >> 4).getChunkSnapshot(true, true, false);
        WorldRef ref = new WorldRef(world.getUID(), world.getName());

        SafeCandidate candidate =
                access.readCandidate(ref, snapshot, world.getMinHeight(), world.getMaxHeight(), blockX, blockZ);

        assertThat(candidate.standingSafe()).isFalse();
        assertThat(candidate.landing()).contains(BlockTypeName.of("water"));
    }

    @Test
    void requestsUnloadOfAChunkTheProbeItselfLoaded() {
        World world = mock(World.class);

        access.releaseIfProbed(world, 3, -2, false);

        verify(world).unloadChunkRequest(3, -2);
    }

    @Test
    void leavesAnAlreadyResidentChunkAlone() {
        World world = mock(World.class);

        access.releaseIfProbed(world, 3, -2, true);

        verify(world, never()).unloadChunkRequest(anyInt(), anyInt());
    }

    @Test
    void flagsAClaimedColumnAsProtectedFromTheClaimSeamOnTheRegionThread() {
        WorldMock world = server.addSimpleWorld("rtp");
        int blockX = 24;
        int blockZ = 24;
        // The claimed land is exactly this column; a candidate built there must carry insideClaim=true so the
        // pure policy can reject it. WorldGuard is absent on MockBukkit, so only the claim seam contributes.
        BukkitChunkAccess claimAware = accessProtectedBy(claimServiceCovering(blockX, blockZ), new NoOpLogger());

        SafeCandidate candidate = claimAware.readCandidate(
                refOf(world),
                snapshotAt(world, blockX, blockZ),
                world.getMinHeight(),
                world.getMaxHeight(),
                blockX,
                blockZ);

        assertThat(candidate.insideClaim()).isTrue();
    }

    @Test
    void leavesAWildernessColumnUnprotected() {
        WorldMock world = server.addSimpleWorld("rtp");
        int blockX = 24;
        int blockZ = 24;
        // The claim seam covers a different column, so this one is wilderness; with WorldGuard also absent the
        // candidate is unprotected.
        BukkitChunkAccess claimAware =
                accessProtectedBy(claimServiceCovering(blockX + 64, blockZ + 64), new NoOpLogger());

        SafeCandidate candidate = claimAware.readCandidate(
                refOf(world),
                snapshotAt(world, blockX, blockZ),
                world.getMinHeight(),
                world.getMaxHeight(),
                blockX,
                blockZ);

        assertThat(candidate.insideClaim()).isFalse();
    }

    @Test
    void leavesTheColumnUnprotectedWhenWorldGuardIsAbsentAndNoClaim() {
        WorldMock world = server.addSimpleWorld("rtp");
        int blockX = 8;
        int blockZ = 8;
        // No claim service objects and WorldGuard is not installed: the reflective region check short-circuits on
        // the present-guard (loading no com.sk89q class) and reports not-protected, so the candidate is free.
        ProtectedLand land = new ClaimAwareProtectedLand(
                claimServiceCovering(-1, -1), new WorldGuardRegions(server, new NoOpLogger()), true, true);
        BukkitChunkAccess access = new BukkitChunkAccess(server, new NoOpLogger(), land);

        SafeCandidate candidate = access.readCandidate(
                refOf(world),
                snapshotAt(world, blockX, blockZ),
                world.getMinHeight(),
                world.getMaxHeight(),
                blockX,
                blockZ);

        assertThat(candidate.insideClaim()).isFalse();
    }

    private BukkitChunkAccess accessProtectedBy(ClaimService claims, Logger log) {
        ProtectedLand land = new ClaimAwareProtectedLand(claims, new WorldGuardRegions(server, log), true, true);
        return new BukkitChunkAccess(server, log, land);
    }

    private static ChunkSnapshot snapshotAt(WorldMock world, int blockX, int blockZ) {
        return world.getChunkAt(blockX >> 4, blockZ >> 4).getChunkSnapshot(true, true, false);
    }

    private static WorldRef refOf(WorldMock world) {
        return new WorldRef(world.getUID(), world.getName());
    }

    /** A claim service that reports exactly one block column claimed; every {@code canPlace}/{@code canAccess} allows. */
    private static ClaimService claimServiceCovering(int blockX, int blockZ) {
        return new ClaimService() {
            @Override
            public ClaimDecision canPlace(PlayerRef who, Position at) {
                return ClaimDecision.ALLOWED;
            }

            @Override
            public ClaimDecision canAccess(PlayerRef who, Position at) {
                return ClaimDecision.ALLOWED;
            }

            @Override
            public boolean isProtected(Position at) {
                return at.blockX() == blockX && at.blockZ() == blockZ;
            }
        };
    }

    private static final class NoOpLogger implements Logger {
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
