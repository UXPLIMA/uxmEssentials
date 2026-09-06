package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProvidersConfig.CombineMode;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * The composite that consults every installed claim plugin at once and folds their answers. The truth tables
 * below drive the {@code any-land} / {@code all-land} combine modes over two and three overlapping fake claims,
 * prove a ban in any member wins under both modes even when another member trusts, prove owner agreement, and
 * prove the selection in {@link ClaimProviders#compose} skips a config-disabled provider even when it is active.
 */
class CompositeClaimProviderTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void anyLand_isTrusted_whenAnyCoveringClaimTrusts() {
        ClaimProvider composite = composite(CombineMode.ANY_LAND, claim().trust(PLAYER), claim());
        assertThat(lookup(composite).isTrusted(PLAYER)).isTrue();
    }

    @Test
    void anyLand_isTrusted_false_whenNoCoveringClaimTrusts() {
        ClaimProvider composite = composite(CombineMode.ANY_LAND, claim(), claim());
        assertThat(lookup(composite).isTrusted(PLAYER)).isFalse();
    }

    @Test
    void allLand_isTrusted_onlyWhenEveryCoveringClaimTrusts() {
        ClaimProvider mixed = composite(CombineMode.ALL_LAND, claim().trust(PLAYER), claim());
        assertThat(lookup(mixed).isTrusted(PLAYER)).isFalse();

        ClaimProvider unanimous = composite(CombineMode.ALL_LAND, claim().trust(PLAYER), claim().trust(PLAYER));
        assertThat(lookup(unanimous).isTrusted(PLAYER)).isTrue();
    }

    @Test
    void anyLand_isTrusted_true_whenAnyOfThreeTrusts() {
        ClaimProvider composite = composite(CombineMode.ANY_LAND, claim(), claim(), claim().trust(PLAYER));
        assertThat(lookup(composite).isTrusted(PLAYER)).isTrue();
    }

    @Test
    void allLand_isTrusted_false_whenOneOfThreeDoesNotTrust() {
        ClaimProvider composite =
                composite(CombineMode.ALL_LAND, claim().trust(PLAYER), claim().trust(PLAYER), claim());
        assertThat(lookup(composite).isTrusted(PLAYER)).isFalse();
    }

    @Test
    void isOwner_foldsPerCombineMode() {
        ClaimProvider anyLand = composite(CombineMode.ANY_LAND, claim().own(PLAYER), claim());
        assertThat(lookup(anyLand).isOwner(PLAYER)).isTrue();
        assertThat(lookup(anyLand).isTrusted(PLAYER)).isTrue();

        ClaimProvider allLand = composite(CombineMode.ALL_LAND, claim().own(PLAYER), claim());
        assertThat(lookup(allLand).isOwner(PLAYER)).isFalse();
    }

    @Test
    void banInAnyMemberDenies_underBothModes_evenWhenAnotherTrusts() {
        ClaimProvider anyLand = composite(CombineMode.ANY_LAND, claim().trust(PLAYER), claim().ban(PLAYER));
        assertThat(lookup(anyLand).isBanned(PLAYER)).isTrue();
        // trust is still reported (any-land), so it is the ban that must win at the policy: proving both are seen.
        assertThat(lookup(anyLand).isTrusted(PLAYER)).isTrue();

        ClaimProvider allLand = composite(CombineMode.ALL_LAND, claim().trust(PLAYER), claim().ban(PLAYER));
        assertThat(lookup(allLand).isBanned(PLAYER)).isTrue();
    }

    @Test
    void owner_emptyWhenCoveringClaimsNameDifferentOwners() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ClaimProvider composite = composite(CombineMode.ANY_LAND, claim().named(first), claim().named(second));
        assertThat(lookup(composite).owner()).isEmpty();
    }

    @Test
    void owner_presentWhenCoveringClaimsAgree_ignoringUnnamedClaims() {
        UUID shared = UUID.randomUUID();
        ClaimProvider composite =
                composite(CombineMode.ANY_LAND, claim().named(shared), claim(), claim().named(shared));
        assertThat(lookup(composite).owner()).contains(shared);
    }

    @Test
    void claimAt_returnsEmpty_whenNoMemberCoversTheBlock() {
        CompositeClaimProvider composite =
                new CompositeClaimProvider(List.of(uncovered(), uncovered()), CombineMode.ANY_LAND);
        assertThat(composite.claimAt(WORLD, 0, 0)).isEmpty();
    }

    @Test
    void emptyComposite_isInactiveAndCoversNothing() {
        CompositeClaimProvider empty = new CompositeClaimProvider(List.of(), CombineMode.ANY_LAND);
        assertThat(empty.active()).isFalse();
        assertThat(empty.claimAt(WORLD, 5, 5)).isEmpty();
    }

    @Test
    void compose_skipsDisabledProvider_evenWhenActive() {
        FakeProvider lands = new FakeProvider(true, Optional.of(claim().trust(PLAYER)));
        FakeProvider grief = new FakeProvider(true, Optional.of(claim()));
        ClaimProvidersConfig config = new ClaimProvidersConfig(Set.of("lands"), CombineMode.ANY_LAND);

        ClaimProvider composed = ClaimProviders.compose(
                config,
                List.of(
                        new ClaimProviders.Candidate("lands", lands),
                        new ClaimProviders.Candidate("griefprevention", grief)),
                noOpLog());

        // Lands trusts the player but is disabled, so the sole consulted claim (grief) does not trust: not trusted.
        assertThat(composed.active()).isTrue();
        assertThat(lookup(composed).isTrusted(PLAYER)).isFalse();
        assertThat(lands.consulted).isFalse();
        assertThat(grief.consulted).isTrue();
    }

    @Test
    void compose_bindsInactive_whenEveryProviderIsDisabled() {
        FakeProvider lands = new FakeProvider(true, Optional.of(claim().trust(PLAYER)));
        ClaimProvidersConfig config = new ClaimProvidersConfig(Set.of("lands"), CombineMode.ANY_LAND);

        ClaimProvider composed =
                ClaimProviders.compose(config, List.of(new ClaimProviders.Candidate("lands", lands)), noOpLog());

        assertThat(composed.active()).isFalse();
        assertThat(composed.claimAt(WORLD, 0, 0)).isEmpty();
    }

    private static ClaimProvider composite(CombineMode combine, ClaimLookup... claims) {
        List<ClaimProvider> members = new java.util.ArrayList<>(claims.length);
        for (ClaimLookup claim : claims) {
            members.add(new FakeProvider(true, Optional.of(claim)));
        }
        return new CompositeClaimProvider(members, combine);
    }

    private static ClaimLookup lookup(ClaimProvider provider) {
        Optional<ClaimLookup> covering = provider.claimAt(WORLD, 0, 0);
        assertThat(covering).isPresent();
        return covering.orElseThrow();
    }

    private static FakeLookup claim() {
        return new FakeLookup();
    }

    private static FakeProvider uncovered() {
        return new FakeProvider(true, Optional.empty());
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

    /** A stub claim whose trust, ownership, ban and named-owner answers are set fluently per test. */
    private static final class FakeLookup implements ClaimLookup {

        private final Set<UUID> trusted = new HashSet<>();
        private final Set<UUID> owners = new HashSet<>();
        private final Set<UUID> banned = new HashSet<>();
        private Optional<UUID> namedOwner = Optional.empty();

        FakeLookup trust(UUID player) {
            trusted.add(player);
            return this;
        }

        FakeLookup own(UUID player) {
            owners.add(player);
            trusted.add(player);
            return this;
        }

        FakeLookup ban(UUID player) {
            banned.add(player);
            return this;
        }

        FakeLookup named(UUID owner) {
            namedOwner = Optional.of(owner);
            return this;
        }

        @Override
        public boolean isTrusted(UUID player) {
            return trusted.contains(player);
        }

        @Override
        public boolean isOwner(UUID player) {
            return owners.contains(player);
        }

        @Override
        public boolean isBanned(UUID player) {
            return banned.contains(player);
        }

        @Override
        public Optional<UUID> owner() {
            return namedOwner;
        }
    }

    /** A stub provider reporting a fixed active state and, optionally, one covering claim; records whether asked. */
    private static final class FakeProvider implements ClaimProvider {

        private final boolean active;
        private final Optional<ClaimLookup> covering;
        private boolean consulted;

        FakeProvider(boolean active, Optional<ClaimLookup> covering) {
            this.active = active;
            this.covering = covering;
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
            consulted = true;
            return covering;
        }
    }
}
