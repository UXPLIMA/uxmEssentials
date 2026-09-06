package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.SuperiorSkyblockClaimProvider.IslandView;
import com.uxplima.uxmessentials.shared.adapter.outbound.claim.SuperiorSkyblockClaimProvider.SuperiorSkyblockClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The SuperiorSkyblock provider with SuperiorSkyblock2 absent, the case on the test classpath, where no
 * {@code com.bgsoftware} class resolves. {@link SuperiorSkyblockClaimProvider#active()} must report inactive
 * without naming a SuperiorSkyblock type and {@link SuperiorSkyblockClaimProvider#claimAt} must degrade to empty,
 * proving the present-guard keeps the reflective SuperiorSkyblock chain from loading on a server without it.
 *
 * <p>The SuperiorSkyblock API chain cannot be stood up under MockBukkit, so the ownership decisions that do not
 * need a live SuperiorSkyblock, an owner, a member-but-not-owner as trust, the real owner UUID, the ban-list
 * ban, and an unknown UUID that is neither a member nor banned. Are exercised against the pure {@link IslandView}
 * seam instead. The unclaimed branch (a live SuperiorSkyblock returning {@code null} from {@code getIslandAt}) and
 * the reflective wrapper resolution ({@code SuperiorSkyblockAPI.getPlayer} returning {@code null} for an unknown
 * UUID) need a running SuperiorSkyblock and are not reproducible here; the seam proves the deny decision an
 * unresolved wrapper must produce.
 */
class SuperiorSkyblockClaimProviderTest {

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
    void active_isFalse_whenSuperiorSkyblockAbsent() {
        SuperiorSkyblockClaimProvider provider = new SuperiorSkyblockClaimProvider(plugin, server, noOpLog());
        assertThat(provider.active()).isFalse();
    }

    @Test
    void claimAt_returnsEmpty_withoutThrowing_whenSuperiorSkyblockAbsent() {
        SuperiorSkyblockClaimProvider provider = new SuperiorSkyblockClaimProvider(plugin, server, noOpLog());
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        assertThatCode(() -> assertThat(provider.claimAt(world, 5, 7)).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void island_ownerOwns_memberIsTrustedButNotOwner() {
        ClaimLookup lookup = lookupOf(new FakeIsland(Optional.of(OWNER), Set.of(MEMBER), Set.of()));

        assertThat(lookup.isOwner(OWNER)).isTrue();
        assertThat(lookup.isTrusted(OWNER)).isTrue();
        assertThat(lookup.isTrusted(MEMBER)).isTrue();
        assertThat(lookup.isOwner(MEMBER)).isFalse();
        assertThat(lookup.isTrusted(STRANGER)).isFalse();
        assertThat(lookup.owner()).contains(OWNER);
    }

    @Test
    void ownerLessIsland_hasNoOwner_andNobodyOwnsIt() {
        ClaimLookup lookup = lookupOf(new FakeIsland(Optional.empty(), Set.of(MEMBER), Set.of()));

        assertThat(lookup.owner()).isEmpty();
        assertThat(lookup.isOwner(OWNER)).isFalse();
        // Membership still confers trust even on an owner-less island.
        assertThat(lookup.isTrusted(MEMBER)).isTrue();
    }

    @Test
    void banned_isBanned_othersAreNot() {
        ClaimLookup lookup = lookupOf(new FakeIsland(Optional.of(OWNER), Set.of(OWNER), Set.of(BANNED)));

        assertThat(lookup.isBanned(BANNED)).isTrue();
        assertThat(lookup.isBanned(OWNER)).isFalse();
        assertThat(lookup.isBanned(STRANGER)).isFalse();
    }

    @Test
    void unknownUuid_withNoWrapper_deniesMemberAndBan() {
        // STRANGER resolves to no SuperiorPlayer wrapper (getPlayer returns null at runtime), which the reflective
        // view reads as neither a member nor banned; the seam models that as absence from both sets.
        ClaimLookup lookup = lookupOf(new FakeIsland(Optional.of(OWNER), Set.of(MEMBER), Set.of(BANNED)));

        assertThat(lookup.isTrusted(STRANGER)).isFalse();
        assertThat(lookup.isBanned(STRANGER)).isFalse();
    }

    private static ClaimLookup lookupOf(IslandView island) {
        return new SuperiorSkyblockClaimLookup(island);
    }

    /**
     * An {@link IslandView} whose answers come from a plain owner UUID, a member set and a ban set, standing in for
     * a real SuperiorSkyblock island. Membership and ban are set containment here; the reflective view instead
     * resolves a {@code SuperiorPlayer} wrapper and defers to {@code Island.isMember} / {@code Island.isBanned},
     * reading a UUID with no wrapper, one absent from both sets, as neither.
     */
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
