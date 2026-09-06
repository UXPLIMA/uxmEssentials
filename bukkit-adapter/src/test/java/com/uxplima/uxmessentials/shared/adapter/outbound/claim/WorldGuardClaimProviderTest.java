package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.WorldGuardClaimProvider.RegionView;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.WorldGuardClaimProvider.WorldGuardClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The WorldGuard provider with WorldGuard absent, the case on the test classpath, where no {@code com.sk89q}
 * class resolves. {@link WorldGuardClaimProvider#active()} must report inactive without naming a WorldGuard type
 * and {@link WorldGuardClaimProvider#claimAt} must degrade to empty, proving the present-guard keeps the
 * reflective region chain from loading on a server without WorldGuard.
 *
 * <p>The region-container chain cannot be stood up under MockBukkit, so the two decisions that do not need a
 * live WorldGuard. Excluding the world-wide {@code __global__} region, and folding owner/member across the
 * covering regions: are exercised against the pure {@link RegionView} seam instead.
 */
class WorldGuardClaimProviderTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void active_isFalse_whenWorldGuardAbsent() {
        WorldGuardClaimProvider provider = new WorldGuardClaimProvider(plugin, server, noOpLog());
        assertThat(provider.active()).isFalse();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenWorldGuardAbsent() {
        WorldGuardClaimProvider provider = new WorldGuardClaimProvider(plugin, server, noOpLog());
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        assertThatCode(() -> assertThat(provider.claimAt(world, 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void namedRegions_dropsTheGlobalRegion() {
        RegionView global = new FakeRegion(WorldGuardClaimProvider.GLOBAL_REGION, Set.of(), Set.of());
        RegionView named = new FakeRegion("spawn", Set.of(ALICE), Set.of());
        assertThat(WorldGuardClaimProvider.namedRegions(List.of(global, named))).containsExactly(named);
    }

    @Test
    void namedRegions_isEmpty_whenOnlyTheGlobalRegionCovers() {
        RegionView global = new FakeRegion(WorldGuardClaimProvider.GLOBAL_REGION, Set.of(ALICE), Set.of());
        assertThat(WorldGuardClaimProvider.namedRegions(List.of(global))).isEmpty();
    }

    @Test
    void isOwner_whenAnyCoveringRegionOwnsThePlayer() {
        ClaimLookup lookup = lookupOf(
                new FakeRegion("plots", Set.of(), Set.of(BOB)), new FakeRegion("shop", Set.of(ALICE), Set.of()));
        assertThat(lookup.isOwner(ALICE)).isTrue();
        assertThat(lookup.isOwner(BOB)).isFalse();
        assertThat(lookup.isOwner(CAROL)).isFalse();
    }

    @Test
    void isTrusted_forOwnerOrMember_butNotStrangers() {
        ClaimLookup lookup = lookupOf(new FakeRegion("plots", Set.of(ALICE), Set.of(BOB)));
        assertThat(lookup.isTrusted(ALICE)).isTrue();
        assertThat(lookup.isTrusted(BOB)).isTrue();
        assertThat(lookup.isTrusted(CAROL)).isFalse();
    }

    @Test
    void isBanned_isAlwaysFalse_andOwnerIsEmpty() {
        ClaimLookup lookup = lookupOf(new FakeRegion("plots", Set.of(ALICE), Set.of()));
        assertThat(lookup.isBanned(ALICE)).isFalse();
        assertThat(lookup.owner()).isEmpty();
    }

    private static ClaimLookup lookupOf(RegionView... regions) {
        return new WorldGuardClaimLookup(List.of(regions));
    }

    /** A {@link RegionView} whose owner/member answers come from plain sets, standing in for a real region. */
    private record FakeRegion(String id, Set<UUID> owners, Set<UUID> members) implements RegionView {

        @Override
        public boolean ownersContains(UUID player) {
            return owners.contains(player);
        }

        @Override
        public boolean membersContains(UUID player) {
            return members.contains(player);
        }
    }

    private static Logger noOpLog() {
        return new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {}

            @Override
            public void debug(String message, Object... args) {}
        };
    }
}
