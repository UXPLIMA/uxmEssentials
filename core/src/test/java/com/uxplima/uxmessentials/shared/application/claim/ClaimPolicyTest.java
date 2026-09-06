package com.uxplima.uxmessentials.shared.application.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ClaimPolicy}. A simple in-memory fake {@link ClaimProvider} models the
 * claim state; each test scenario is set up by registering block coordinates against a
 * trusted/banned owner pair.
 */
class ClaimPolicyTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeClaimProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FakeClaimProvider();
    }

    // -----------------------------------------------------------------------
    // Provider inactive
    // -----------------------------------------------------------------------

    @Test
    void inactiveProvider_canPlace_isAllowed() {
        provider.setActive(false);
        ClaimPolicy policy = new ClaimPolicy(provider, ClaimPolicySettings.defaults());

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.ALLOWED);
    }

    @Test
    void inactiveProvider_canAccess_isAllowed() {
        provider.setActive(false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, false, 0, true);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canAccess(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.ALLOWED);
    }

    // -----------------------------------------------------------------------
    // canPlace, claim present
    // -----------------------------------------------------------------------

    @Test
    void trustedInClaim_canPlace_isAllowed() {
        provider.addClaim(0, 0, PLAYER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, true, 0, false);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.ALLOWED);
    }

    @Test
    void foreignClaimWithBlockForeign_canPlace_isDeniedForeign() {
        provider.addClaim(0, 0, OWNER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, true, 0, false);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.DENIED_FOREIGN);
    }

    @Test
    void foreignClaimWithoutBlockForeign_canPlace_isAllowed() {
        provider.addClaim(0, 0, OWNER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, false, 0, false);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.ALLOWED);
    }

    @Test
    void bannedFromClaim_canPlace_isDeniedAccess() {
        // PLAYER is a trusted member but banned: the ban denies placement ahead of the trust test.
        provider.addClaimTrusting(0, 0, OWNER, PLAYER, true);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, true, 0, false);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.DENIED_ACCESS);
    }

    // -----------------------------------------------------------------------
    // canPlace. Owner-only membership (playerwarps "own-land" mode)
    // -----------------------------------------------------------------------

    @Test
    void ownerOnly_trustedButNotOwner_canPlace_isDeniedForeign() {
        // PLAYER is trusted in OWNER's claim but does not own it.
        provider.addClaimTrusting(0, 0, OWNER, PLAYER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, true, 0, false, true);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.DENIED_FOREIGN);
    }

    @Test
    void ownerOrTrusted_trustedButNotOwner_canPlace_isAllowed() {
        // Same claim and player, but owner-or-member membership admits the trusted non-owner.
        provider.addClaimTrusting(0, 0, OWNER, PLAYER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, true, 0, false, false);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.ALLOWED);
    }

    @Test
    void ownerOnly_owner_canPlace_isAllowed() {
        provider.addClaim(0, 0, PLAYER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, true, 0, false, true);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.ALLOWED);
    }

    @Test
    void ownerOnly_proximityToTrustedButNotOwnedClaim_canPlace_isDeniedTooClose() {
        // Neighbouring chunk (1,0) holds a claim PLAYER is trusted in but does not own.
        provider.addClaimTrusting(16, 0, OWNER, PLAYER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, false, 1, false, true);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.DENIED_TOO_CLOSE);
    }

    @Test
    void ownerOrTrusted_proximityToTrustedClaim_canPlace_isAllowed() {
        // The same neighbouring trusted claim is not foreign under owner-or-member membership.
        provider.addClaimTrusting(16, 0, OWNER, PLAYER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, false, 1, false, false);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.ALLOWED);
    }

    // -----------------------------------------------------------------------
    // canPlace, unclaimed land
    // -----------------------------------------------------------------------

    @Test
    void unclaimedWithRequireClaim_canPlace_isDeniedRequired() {
        ClaimPolicySettings settings = new ClaimPolicySettings(true, false, 0, false);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.DENIED_REQUIRED);
    }

    @Test
    void unclaimedWithProximityToForeignClaim_canPlace_isDeniedTooClose() {
        // Claim is one chunk away (chunk 1,0 = block 16,0).
        provider.addClaim(16, 0, OWNER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, false, 1, false);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        // Target block is at (0, 0): base chunk (0, 0); neighbouring chunk (1, 0) has a foreign claim.
        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.DENIED_TOO_CLOSE);
    }

    @Test
    void unclaimedWithProximityToOwnClaim_canPlace_isAllowed() {
        // Nearby claim belongs to the same player, not a foreign claim.
        provider.addClaim(16, 0, PLAYER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, false, 1, false);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canPlace(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.ALLOWED);
    }

    // -----------------------------------------------------------------------
    // canAccess
    // -----------------------------------------------------------------------

    @Test
    void checkTeleportAccessOff_canAccess_isAlwaysAllowed() {
        provider.addClaim(0, 0, OWNER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, false, 0, false);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canAccess(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.ALLOWED);
    }

    @Test
    void notTrustedInClaim_canAccess_isDeniedAccess() {
        provider.addClaim(0, 0, OWNER, false);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, false, 0, true);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canAccess(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.DENIED_ACCESS);
    }

    @Test
    void bannedFromClaim_canAccess_isDeniedAccess() {
        // PLAYER is a trusted member here, so only the ban can deny access: the trust test would allow it.
        provider.addClaimTrusting(0, 0, OWNER, PLAYER, true);
        ClaimPolicySettings settings = new ClaimPolicySettings(false, false, 0, true);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canAccess(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.DENIED_ACCESS);
    }

    @Test
    void noClaim_canAccess_isAllowed() {
        ClaimPolicySettings settings = new ClaimPolicySettings(false, false, 0, true);
        ClaimPolicy policy = new ClaimPolicy(provider, settings);

        assertThat(policy.canAccess(PLAYER, WORLD, 0, 0)).isEqualTo(ClaimDecision.ALLOWED);
    }

    // -----------------------------------------------------------------------
    // isWithinClaim. The player-agnostic "is this land claimed at all" check RTP uses
    // -----------------------------------------------------------------------

    @Test
    void inactiveProvider_isWithinClaim_isFalse() {
        provider.addClaim(0, 0, OWNER, false);
        provider.setActive(false);
        ClaimPolicy policy = new ClaimPolicy(provider, ClaimPolicySettings.defaults());

        assertThat(policy.isWithinClaim(WORLD, 0, 0)).isFalse();
    }

    @Test
    void aClaimedBlock_isWithinClaim_isTrueRegardlessOfOwnerOrSettings() {
        // Owned by someone else, and every placement knob is inert: presence-of-claim is all that matters.
        provider.addClaim(0, 0, OWNER, false);
        ClaimPolicy policy = new ClaimPolicy(provider, ClaimPolicySettings.defaults());

        assertThat(policy.isWithinClaim(WORLD, 0, 0)).isTrue();
    }

    @Test
    void unclaimedWilderness_isWithinClaim_isFalse() {
        ClaimPolicy policy = new ClaimPolicy(provider, ClaimPolicySettings.defaults());

        assertThat(policy.isWithinClaim(WORLD, 0, 0)).isFalse();
    }

    // -----------------------------------------------------------------------
    // ClaimPolicySettings. OwnerOnly field, delegating constructor, equality
    // -----------------------------------------------------------------------

    @Test
    void settings_equalsAndHashCode_distinguishOwnerOnly() {
        ClaimPolicySettings ownerOrTrusted = new ClaimPolicySettings(false, true, 0, false, false);
        ClaimPolicySettings ownerOnly = new ClaimPolicySettings(false, true, 0, false, true);

        assertThat(ownerOrTrusted).isNotEqualTo(ownerOnly);
        assertThat(ownerOrTrusted.hashCode()).isNotEqualTo(ownerOnly.hashCode());
    }

    @Test
    void settings_delegatingConstructor_equalsFiveArgWithOwnerOnlyFalse() {
        assertThat(new ClaimPolicySettings(true, true, 2, true))
                .isEqualTo(new ClaimPolicySettings(true, true, 2, true, false))
                .extracting(ClaimPolicySettings::ownerOnly)
                .isEqualTo(false);
    }

    @Test
    void settings_defaults_areNotOwnerOnly() {
        assertThat(ClaimPolicySettings.defaults().ownerOnly()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Fake provider
    // -----------------------------------------------------------------------

    /**
     * Simple fake backed by a map of (blockX, blockZ) → claim. A claim has one owner UUID, the set
     * of players trusted in it (which always includes the owner), and an optional ban for the test
     * PLAYER. Owner-only scenarios register a claim whose trusted set includes a member who is not
     * the owner, so {@code isTrusted} and {@code isOwner} genuinely diverge.
     */
    private static final class FakeClaimProvider implements ClaimProvider {

        private boolean active = true;
        // key: blockX * 100_000 + blockZ (safe for small test coordinates)
        private final Map<Long, FakeLookup> claims = new HashMap<>();

        void setActive(boolean active) {
            this.active = active;
        }

        /** Register a claim at the given block coordinates owned by {@code owner}, with only the owner trusted. */
        void addClaim(int blockX, int blockZ, UUID owner, boolean banTestPlayer) {
            claims.put(key(blockX, blockZ), new FakeLookup(owner, Set.of(owner), banTestPlayer));
        }

        /**
         * Register a claim owned by {@code owner} but with {@code member} also trusted, so {@code member} is a
         * trusted-but-not-owner player: the case that separates owner-only placement from owner-or-member.
         */
        void addClaimTrusting(int blockX, int blockZ, UUID owner, UUID member, boolean banTestPlayer) {
            claims.put(key(blockX, blockZ), new FakeLookup(owner, Set.of(owner, member), banTestPlayer));
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
            FakeLookup lookup = claims.get(key(blockX, blockZ));
            return Optional.ofNullable(lookup);
        }

        private static long key(int blockX, int blockZ) {
            return ((long) blockX << 32) | (blockZ & 0xFFFFFFFFL);
        }
    }

    private static final class FakeLookup implements ClaimLookup {

        private final UUID owner;
        private final Set<UUID> trusted;
        private final boolean banTestPlayer;

        FakeLookup(UUID owner, Set<UUID> trusted, boolean banTestPlayer) {
            this.owner = owner;
            this.trusted = trusted;
            this.banTestPlayer = banTestPlayer;
        }

        @Override
        public boolean isTrusted(UUID player) {
            return trusted.contains(player);
        }

        @Override
        public boolean isOwner(UUID player) {
            return owner.equals(player);
        }

        @Override
        public boolean isBanned(UUID player) {
            return banTestPlayer && PLAYER.equals(player);
        }
    }
}
