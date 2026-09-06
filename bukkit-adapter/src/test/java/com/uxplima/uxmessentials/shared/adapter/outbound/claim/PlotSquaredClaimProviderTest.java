package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.PlotSquaredClaimProvider.PlotSquaredClaimLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.PlotSquaredClaimProvider.PlotView;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The PlotSquared provider with PlotSquared absent, the case on the test classpath, where no
 * {@code com.plotsquared} class resolves. {@link PlotSquaredClaimProvider#active()} must report inactive without
 * naming a PlotSquared type and {@link PlotSquaredClaimProvider#claimAt} must degrade to empty, proving the
 * present-guard keeps the reflective PlotSquared chain from loading on a server without PlotSquared.
 *
 * <p>The PlotSquared API chain cannot be stood up under MockBukkit, so the ownership decisions that do not need a
 * live PlotSquared, an owner, an added-but-not-owner member as trust, the real owner UUID (empty when unowned),
 * and the deny-list ban: are exercised against the pure {@link PlotView} seam instead. The unclaimed branch (a
 * live PlotSquared returning {@code null} from {@code getOwnedPlot}) needs a running PlotSquared and is not
 * reproducible here.
 */
class PlotSquaredClaimProviderTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID ADDED = UUID.randomUUID();
    private static final UUID DENIED = UUID.randomUUID();
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
    void active_isFalse_whenPlotSquaredAbsent() {
        PlotSquaredClaimProvider provider = new PlotSquaredClaimProvider(plugin, server, noOpLog());
        assertThat(provider.active()).isFalse();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenPlotSquaredAbsent() {
        PlotSquaredClaimProvider provider = new PlotSquaredClaimProvider(plugin, server, noOpLog());
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        assertThatCode(() -> assertThat(provider.claimAt(world, 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void ownedPlot_ownerOwns_addedMemberIsTrustedButNotOwner() {
        ClaimLookup lookup = lookupOf(new FakePlot(Optional.of(OWNER), Set.of(OWNER, ADDED), Set.of()));

        assertThat(lookup.isOwner(OWNER)).isTrue();
        assertThat(lookup.isTrusted(OWNER)).isTrue();
        assertThat(lookup.isTrusted(ADDED)).isTrue();
        assertThat(lookup.isOwner(ADDED)).isFalse();
        assertThat(lookup.isTrusted(STRANGER)).isFalse();
        assertThat(lookup.owner()).contains(OWNER);
    }

    @Test
    void unownedPlot_hasNoOwner_andNobodyOwnsIt() {
        ClaimLookup lookup = lookupOf(new FakePlot(Optional.empty(), Set.of(ADDED), Set.of()));

        assertThat(lookup.owner()).isEmpty();
        assertThat(lookup.isOwner(OWNER)).isFalse();
        // Being added still confers trust even on an owner-less plot.
        assertThat(lookup.isTrusted(ADDED)).isTrue();
    }

    @Test
    void denied_isBanned_othersAreNot() {
        ClaimLookup lookup = lookupOf(new FakePlot(Optional.of(OWNER), Set.of(OWNER), Set.of(DENIED)));

        assertThat(lookup.isBanned(DENIED)).isTrue();
        assertThat(lookup.isBanned(OWNER)).isFalse();
        assertThat(lookup.isBanned(STRANGER)).isFalse();
    }

    private static ClaimLookup lookupOf(PlotView plot) {
        return new PlotSquaredClaimLookup(plot);
    }

    /**
     * A {@link PlotView} whose answers come from a plain owner UUID, an added set and a denied set, standing in
     * for a real PlotSquared plot. Ownership is single-owner equality here; the reflective view instead defers to
     * {@code Plot.isOwner}, which additionally spans a merged plot's co-owners.
     */
    private record FakePlot(Optional<UUID> owner, Set<UUID> added, Set<UUID> denied) implements PlotView {

        @Override
        public boolean isOwner(UUID player) {
            return owner.map(player::equals).orElse(false);
        }

        @Override
        public boolean isAdded(UUID player) {
            return added.contains(player);
        }

        @Override
        public boolean isDenied(UUID player) {
            return denied.contains(player);
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
