package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.TownyClaimProvider.PlotView;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.TownyClaimProvider.TownyClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The Towny provider with Towny absent, the case on the test classpath, where no {@code com.palmergames} class
 * resolves. {@link TownyClaimProvider#active()} must report inactive without naming a Towny type and {@link
 * TownyClaimProvider#claimAt} must degrade to empty, proving the present-guard keeps the reflective Towny chain
 * from loading on a server without Towny.
 *
 * <p>The Towny API chain cannot be stood up under MockBukkit, so the ownership decisions that do not need a live
 * Towny. A personal-owner plot, a town-owned plot (mayor stands in), town membership as trust, and the outlaw
 * ban: are exercised against the pure {@link PlotView} seam instead. The wilderness branch (a live Towny
 * returning a {@code null} town block) needs a running Towny and is not reproducible here.
 */
class TownyClaimProviderTest {

    private static final UUID MAYOR = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID OUTLAW = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

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
    void active_isFalse_whenTownyAbsent() {
        TownyClaimProvider provider = new TownyClaimProvider(plugin, server, noOpLog());
        assertThat(provider.active()).isFalse();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenTownyAbsent() {
        TownyClaimProvider provider = new TownyClaimProvider(plugin, server, noOpLog());
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        assertThatCode(() -> assertThat(provider.claimAt(world, 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void personalOwnerPlot_ownerOwns_memberIsTrustedButNotOwner() {
        ClaimLookup lookup = lookupOf(new FakePlot(Optional.of(OWNER), Optional.of(MAYOR), Set.of(MEMBER), Set.of()));

        assertThat(lookup.isOwner(OWNER)).isTrue();
        assertThat(lookup.isTrusted(OWNER)).isTrue();
        assertThat(lookup.isTrusted(MEMBER)).isTrue();
        assertThat(lookup.isOwner(MEMBER)).isFalse();
        assertThat(lookup.isTrusted(STRANGER)).isFalse();
        assertThat(lookup.owner()).contains(OWNER);
    }

    @Test
    void townOwnedPlot_mayorOwnsWhenNoPersonalOwner() {
        ClaimLookup lookup = lookupOf(new FakePlot(Optional.empty(), Optional.of(MAYOR), Set.of(MEMBER), Set.of()));

        assertThat(lookup.isOwner(MAYOR)).isTrue();
        assertThat(lookup.owner()).contains(MAYOR);
        assertThat(lookup.isTrusted(MEMBER)).isTrue();
        assertThat(lookup.isOwner(MEMBER)).isFalse();
        assertThat(lookup.isTrusted(STRANGER)).isFalse();
    }

    @Test
    void outlaw_isBanned_othersAreNot() {
        ClaimLookup lookup =
                lookupOf(new FakePlot(Optional.of(OWNER), Optional.of(MAYOR), Set.of(MEMBER), Set.of(OUTLAW)));

        assertThat(lookup.isBanned(OUTLAW)).isTrue();
        assertThat(lookup.isBanned(OWNER)).isFalse();
        assertThat(lookup.isBanned(MEMBER)).isFalse();
    }

    private static ClaimLookup lookupOf(PlotView plot) {
        return new TownyClaimLookup(plot);
    }

    /** A {@link PlotView} whose answers come from plain fields/sets, standing in for a real Towny town plot. */
    private record FakePlot(Optional<UUID> personalOwner, Optional<UUID> mayor, Set<UUID> residents, Set<UUID> outlaws)
            implements PlotView {

        @Override
        public boolean isResident(UUID player) {
            return residents.contains(player);
        }

        @Override
        public boolean isOutlaw(UUID player) {
            return outlaws.contains(player);
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
