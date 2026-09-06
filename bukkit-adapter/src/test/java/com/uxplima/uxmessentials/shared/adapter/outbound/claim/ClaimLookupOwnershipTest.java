package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import me.angeschossen.lands.api.land.Area;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Proves the ownership contract each provider now supplies: {@code isOwner} answers owner-only, distinct from
 * {@code isTrusted}'s owner-or-member. The load-bearing case is a player who is trusted but does not own the
 * claim: {@code isTrusted} must stay {@code true} while {@code isOwner} turns {@code false}.
 *
 * <p>The two typed providers (Lands, GriefPrevention) are driven through a stubbed SDK {@code Area}/{@code
 * Claim}. The reflective providers hold their claim as a plain {@code Object} and reach it through cached
 * {@link Method} handles; with no plugin SDK on the test classpath those handles never resolve on their own,
 * so each test injects handles pointing at a local fake with the methods the provider expects and constructs
 * the (package-private) lookup directly. That exercises the real {@code isOwner}/{@code owner} bodies without
 * standing up the plugin, and adds no SDK class name to the classpath. Leaving the lazy-load proofs in
 * {@link ClaimProvidersTest} untouched.
 */
class ClaimLookupOwnershipTest {

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
    void lands_ownerIsDistinctFromTrustedMember() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Area area = mock(Area.class);
        when(area.getOwnerUID()).thenReturn(owner);
        when(area.isTrusted(owner)).thenReturn(true);
        when(area.isTrusted(member)).thenReturn(true);

        ClaimLookup lookup = new LandsClaimProvider.LandsClaimLookup(area);

        assertOwnerTrustedGap(lookup, owner, member);
        assertThat(lookup.owner()).contains(owner);
    }

    @Test
    void griefPrevention_ownerIsDistinctFromTrustedBuilder() {
        UUID owner = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        Claim claim = mock(Claim.class);
        when(claim.getOwnerID()).thenReturn(owner);
        when(claim.hasExplicitPermission(builder, ClaimPermission.Build)).thenReturn(true);

        ClaimLookup lookup = new GriefPreventionClaimProvider.GriefPreventionClaimLookup(claim);

        assertOwnerTrustedGap(lookup, owner, builder);
        assertThat(lookup.owner()).contains(owner);
    }

    @Test
    void griefPrevention_adminClaimHasNoOwner() {
        // An admin claim reports a null owner id; no player owns it and owner() is empty.
        Claim claim = mock(Claim.class);
        ClaimLookup lookup = new GriefPreventionClaimProvider.GriefPreventionClaimLookup(claim);

        assertThat(lookup.isOwner(UUID.randomUUID())).isFalse();
        assertThat(lookup.owner()).isEmpty();
    }

    @Test
    void uxmClaims_ownerIsDistinctFromTrustedMember() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UxmClaimsClaimProvider provider = new UxmClaimsClaimProvider(plugin, server, noOpLog());
        inject(provider, "isOwner", UxmFakeClaim.class.getMethod("isOwner", UUID.class));
        inject(provider, "findMemberByUid", UxmFakeClaim.class.getMethod("findMemberByUid", UUID.class));
        inject(provider, "hasBanByUid", UxmFakeClaim.class.getMethod("hasBanByUid", UUID.class));

        ClaimLookup lookup = objectLookup(provider, "UxmClaimLookup", new UxmFakeClaim(owner, Set.of(member)));

        assertOwnerTrustedGap(lookup, owner, member);
        // uxmClaims exposes an ownership predicate but no single owner-UUID accessor, so owner() stays empty.
        assertThat(lookup.owner()).isEmpty();
    }

    @Test
    void griefDefender_ownerIsDistinctFromTrustedMember() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        GriefDefenderClaimProvider provider = new GriefDefenderClaimProvider(plugin, server, noOpLog());
        inject(provider, "getOwnerUniqueId", GriefDefenderFakeClaim.class.getMethod("getOwnerUniqueId"));
        inject(
                provider,
                "isUserTrusted",
                GriefDefenderFakeClaim.class.getMethod("isUserTrusted", UUID.class, Object.class));
        inject(provider, "builderTrustType", new Object());

        ClaimLookup lookup =
                objectLookup(provider, "GriefDefenderClaimLookup", new GriefDefenderFakeClaim(owner, Set.of(member)));

        assertOwnerTrustedGap(lookup, owner, member);
        assertThat(lookup.owner()).contains(owner);
    }

    @Test
    void excellentClaims_ownerIsDistinctFromTrustedMember() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        ExcellentClaimsClaimProvider provider = new ExcellentClaimsClaimProvider(plugin, server, noOpLog());
        inject(provider, "getOwnerId", ExcellentFakeClaim.class.getMethod("getOwnerId"));
        inject(provider, "isOwnerOrMember", ExcellentFakeClaim.class.getMethod("isOwnerOrMember", UUID.class));

        ClaimLookup lookup =
                objectLookup(provider, "ExcellentClaimsClaimLookup", new ExcellentFakeClaim(owner, Set.of(member)));

        assertOwnerTrustedGap(lookup, owner, member);
        assertThat(lookup.owner()).contains(owner);
    }

    @Test
    void xclaim_ownerIsDistinctFromTrustedBuilder() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        XClaimClaimProvider provider = new XClaimClaimProvider(plugin, server, noOpLog());
        inject(provider, "getOwner", XClaimFakeClaim.class.getMethod("getOwner"));
        inject(provider, "getOwnerUniqueId", XClaimFakeOwner.class.getMethod("getUniqueId"));
        inject(
                provider,
                "getUserPermission",
                XClaimFakeClaim.class.getMethod("getUserPermission", OfflinePlayer.class, Object.class));
        inject(provider, "buildPermission", new Object());

        ClaimLookup lookup = objectLookup(
                provider, "XClaimClaimLookup", new XClaimFakeClaim(new XClaimFakeOwner(owner), Set.of(builder)));

        assertOwnerTrustedGap(lookup, owner, builder);
        assertThat(lookup.owner()).contains(owner);
    }

    @Test
    void homestead_ownerIsDistinctFromTrustedRegionMember() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        HomesteadClaimProvider provider = new HomesteadClaimProvider(plugin, server, noOpLog());
        inject(
                provider,
                "isMemberOfRegion",
                HomesteadFakeMembers.class.getMethod("isMemberOfRegion", long.class, UUID.class));

        // A region whose id the fake treats as "everyone is a member". The owner is trusted by identity, the
        // member by region membership, yet only the owner passes isOwner.
        ClaimLookup lookup = homesteadLookup(provider, HomesteadFakeMembers.MEMBER_REGION, owner);

        assertOwnerTrustedGap(lookup, owner, member);
        assertThat(lookup.owner()).contains(owner);
    }

    // The whole point of the feature: the trusted member is trusted but not the owner, and isOwner implies
    // isTrusted for the actual owner.
    private static void assertOwnerTrustedGap(ClaimLookup lookup, UUID owner, UUID trustedNonOwner) {
        assertThat(lookup.isTrusted(owner)).isTrue();
        assertThat(lookup.isOwner(owner)).isTrue();
        assertThat(lookup.isTrusted(trustedNonOwner)).isTrue();
        assertThat(lookup.isOwner(trustedNonOwner)).isFalse();
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field handle = target.getClass().getDeclaredField(field);
        handle.setAccessible(true);
        handle.set(target, value);
    }

    private static ClaimLookup objectLookup(Object provider, String innerName, Object claim) throws Exception {
        Class<?> outer = provider.getClass();
        Class<?> inner = Class.forName(outer.getName() + "$" + innerName);
        Constructor<?> ctor = inner.getDeclaredConstructor(outer, Object.class);
        ctor.setAccessible(true);
        return (ClaimLookup) ctor.newInstance(provider, claim);
    }

    private static ClaimLookup homesteadLookup(HomesteadClaimProvider provider, long regionId, UUID owner)
            throws Exception {
        Class<?> inner = Class.forName(HomesteadClaimProvider.class.getName() + "$HomesteadClaimLookup");
        Constructor<?> ctor = inner.getDeclaredConstructor(HomesteadClaimProvider.class, long.class, UUID.class);
        ctor.setAccessible(true);
        return (ClaimLookup) ctor.newInstance(provider, regionId, owner);
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

    /** Mirrors the uxmClaims {@code Claim} surface the provider reflects against. */
    public static final class UxmFakeClaim {
        private final UUID owner;
        private final Set<UUID> members;

        UxmFakeClaim(UUID owner, Set<UUID> members) {
            this.owner = owner;
            this.members = members;
        }

        public boolean isOwner(UUID player) {
            return owner.equals(player);
        }

        public Optional<Object> findMemberByUid(UUID player) {
            return members.contains(player) ? Optional.of(new Object()) : Optional.empty();
        }

        public boolean hasBanByUid(UUID player) {
            return false;
        }
    }

    /** Mirrors the GriefDefender {@code Claim} surface: owner uid plus a trust check that ignores the type. */
    public static final class GriefDefenderFakeClaim {
        private final UUID owner;
        private final Set<UUID> trusted;

        GriefDefenderFakeClaim(UUID owner, Set<UUID> trusted) {
            this.owner = owner;
            this.trusted = trusted;
        }

        public UUID getOwnerUniqueId() {
            return owner;
        }

        public boolean isUserTrusted(UUID player, Object trustType) {
            return trusted.contains(player);
        }
    }

    /** Mirrors the ExcellentClaims {@code Claim} surface. */
    public static final class ExcellentFakeClaim {
        private final UUID owner;
        private final Set<UUID> members;

        ExcellentFakeClaim(UUID owner, Set<UUID> members) {
            this.owner = owner;
            this.members = members;
        }

        public UUID getOwnerId() {
            return owner;
        }

        public boolean isOwnerOrMember(UUID player) {
            return owner.equals(player) || members.contains(player);
        }
    }

    /** Mirrors XClaim's owner handle ({@code XCPlayer#getUniqueId}). */
    public static final class XClaimFakeOwner {
        private final UUID id;

        XClaimFakeOwner(UUID id) {
            this.id = id;
        }

        public UUID getUniqueId() {
            return id;
        }
    }

    /** Mirrors the XClaim {@code Claim} surface: an owner handle and a per-player BUILD grant. */
    public static final class XClaimFakeClaim {
        private final XClaimFakeOwner owner;
        private final Set<UUID> builders;

        XClaimFakeClaim(XClaimFakeOwner owner, Set<UUID> builders) {
            this.owner = owner;
            this.builders = builders;
        }

        public XClaimFakeOwner getOwner() {
            return owner;
        }

        public boolean getUserPermission(OfflinePlayer player, Object permission) {
            return builders.contains(player.getUniqueId());
        }
    }

    /** Mirrors Homestead's static {@code MemberManager.isMemberOfRegion}. */
    public static final class HomesteadFakeMembers {
        static final long MEMBER_REGION = 42L;

        public static boolean isMemberOfRegion(long regionId, UUID player) {
            return regionId == MEMBER_REGION;
        }
    }
}
