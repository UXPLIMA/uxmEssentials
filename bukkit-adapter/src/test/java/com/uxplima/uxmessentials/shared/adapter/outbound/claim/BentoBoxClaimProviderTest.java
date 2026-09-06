package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.BentoBoxClaimProvider.BentoBoxClaimLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.BentoBoxClaimProvider.IslandView;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The BentoBox provider with BentoBox absent, the case on the test classpath, where no {@code world.bentobox}
 * class resolves. {@link BentoBoxClaimProvider#active()} must report inactive without naming a BentoBox type and
 * {@link BentoBoxClaimProvider#claimAt} must degrade to empty, proving the present-guard keeps the reflective
 * BentoBox chain from loading on a server without BentoBox.
 *
 * <p>The BentoBox API chain cannot be stood up under MockBukkit, so the ownership decisions that do not need a
 * live BentoBox (an owned island, an unowned/spawn island (no owner), island membership as trust, and the ban)
 * are exercised against the pure {@link IslandView} seam instead. The off-island branch (a live BentoBox
 * returning an empty {@code getIslandAt}) needs a running BentoBox and is not reproducible here.
 */
class BentoBoxClaimProviderTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID BANNED = UUID.randomUUID();
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
    void active_isFalse_whenBentoBoxAbsent() {
        BentoBoxClaimProvider provider = new BentoBoxClaimProvider(plugin, server, noOpLog());
        assertThat(provider.active()).isFalse();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenBentoBoxAbsent() {
        BentoBoxClaimProvider provider = new BentoBoxClaimProvider(plugin, server, noOpLog());
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        assertThatCode(() -> assertThat(provider.claimAt(world, 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void ownedIsland_ownerOwns_memberIsTrustedButNotOwner() {
        ClaimLookup lookup = lookupOf(new FakeIsland(Optional.of(OWNER), Set.of(MEMBER), Set.of()));

        assertThat(lookup.isOwner(OWNER)).isTrue();
        assertThat(lookup.isTrusted(OWNER)).isTrue();
        assertThat(lookup.isTrusted(MEMBER)).isTrue();
        assertThat(lookup.isOwner(MEMBER)).isFalse();
        assertThat(lookup.isTrusted(STRANGER)).isFalse();
        assertThat(lookup.owner()).contains(OWNER);
    }

    @Test
    void unownedIsland_hasNoOwner_andNobodyOwnsIt() {
        ClaimLookup lookup = lookupOf(new FakeIsland(Optional.empty(), Set.of(MEMBER), Set.of()));

        assertThat(lookup.owner()).isEmpty();
        assertThat(lookup.isOwner(OWNER)).isFalse();
        // Membership still confers trust even on an owner-less island.
        assertThat(lookup.isTrusted(MEMBER)).isTrue();
    }

    @Test
    void banned_isBanned_othersAreNot() {
        ClaimLookup lookup = lookupOf(new FakeIsland(Optional.of(OWNER), Set.of(MEMBER), Set.of(BANNED)));

        assertThat(lookup.isBanned(BANNED)).isTrue();
        assertThat(lookup.isBanned(OWNER)).isFalse();
        assertThat(lookup.isBanned(MEMBER)).isFalse();
    }

    private static ClaimLookup lookupOf(IslandView island) {
        return new BentoBoxClaimLookup(island);
    }

    /** An {@link IslandView} whose answers come from plain fields/sets, standing in for a real BentoBox island. */
    private record FakeIsland(Optional<UUID> owner, Set<UUID> members, Set<UUID> banned) implements IslandView {

        @Override
        public boolean isMember(UUID player) {
            return members.contains(player);
        }

        @Override
        public boolean isBanned(UUID player) {
            return banned.contains(player);
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
