package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.adapter.outbound.claim.ClaimProvidersConfig.CombineMode;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link ClaimProvider} that consults every installed-and-enabled claim plugin at once rather than the first
 * one discovered, so an operator running, say, Lands and WorldGuard together gets both consulted. It holds the
 * active member providers and, for a queried block, gathers the claim each reports there into one
 * {@link CompositeClaimLookup} that folds their answers per the operator's {@link CombineMode}.
 *
 * <p>With no members it reports inactive and empty, exactly like the no-op provider, so an install with no
 * claim plugin still allows everything.
 */
@NullMarked
final class CompositeClaimProvider implements ClaimProvider {

    private final List<ClaimProvider> members;
    private final CombineMode combine;

    CompositeClaimProvider(List<ClaimProvider> members, CombineMode combine) {
        Objects.requireNonNull(members, "members");
        this.members = List.copyOf(members);
        this.combine = Objects.requireNonNull(combine, "combine");
    }

    @Override
    public boolean active() {
        for (ClaimProvider member : members) {
            if (member.active()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(world, "world");
        List<ClaimLookup> covering = new ArrayList<>(members.size());
        for (ClaimProvider member : members) {
            member.claimAt(world, blockX, blockZ).ifPresent(covering::add);
        }
        return covering.isEmpty() ? Optional.empty() : Optional.of(new CompositeClaimLookup(covering, combine));
    }

    /**
     * The folded view of every claim covering one block. Trust and ownership honour the {@link CombineMode}
     * (any covering claim, or all of them); a ban in any covering claim always wins, so a banned player is
     * denied regardless of trust and once a ban is seen the remaining claims need not be consulted.
     */
    static final class CompositeClaimLookup implements ClaimLookup {

        private final List<ClaimLookup> covering;
        private final CombineMode combine;

        CompositeClaimLookup(List<ClaimLookup> covering, CombineMode combine) {
            this.covering = List.copyOf(covering);
            this.combine = Objects.requireNonNull(combine, "combine");
        }

        @Override
        public boolean isTrusted(UUID player) {
            Objects.requireNonNull(player, "player");
            return fold(lookup -> lookup.isTrusted(player));
        }

        @Override
        public boolean isOwner(UUID player) {
            Objects.requireNonNull(player, "player");
            return fold(lookup -> lookup.isOwner(player));
        }

        @Override
        public boolean isBanned(UUID player) {
            Objects.requireNonNull(player, "player");
            for (ClaimLookup lookup : covering) {
                if (lookup.isBanned(player)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Optional<UUID> owner() {
            UUID agreed = null;
            for (ClaimLookup lookup : covering) {
                Optional<UUID> named = lookup.owner();
                if (named.isEmpty()) {
                    continue;
                }
                if (agreed == null) {
                    agreed = named.get();
                } else if (!agreed.equals(named.get())) {
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(agreed);
        }

        private boolean fold(Predicate<ClaimLookup> test) {
            return combine == CombineMode.ALL_LAND
                    ? covering.stream().allMatch(test)
                    : covering.stream().anyMatch(test);
        }
    }
}
