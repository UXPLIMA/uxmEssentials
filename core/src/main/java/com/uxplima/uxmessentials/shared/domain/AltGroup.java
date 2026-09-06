package com.uxplima.uxmessentials.shared.domain;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The set of accounts that share at least one IP token with a target account: the target's alts. It is derived
 * purely from a collection of {@link IpAssociation}s, so the grouping rule is unit-testable without a database:
 * find the tokens the target has ever connected from, then collect every other account seen on any of those
 * tokens. The target itself is never listed among its own alts.
 *
 * @param account the account the group is centred on
 * @param alts the other accounts sharing an IP token with {@code account} (never containing {@code account})
 */
public record AltGroup(UUID account, Set<UUID> alts) {

    public AltGroup(UUID account, Set<UUID> alts) {
        this.account = Objects.requireNonNull(account, "account");
        Objects.requireNonNull(alts, "alts");
        this.alts = Set.copyOf(alts);
    }

    /** Whether the account shares an IP with no other account. */
    public boolean isEmpty() {
        return alts.isEmpty();
    }

    /**
     * Group {@code account}'s alts out of {@code associations}: the accounts (other than {@code account}) that
     * appear on any IP token {@code account} itself appears on. Associations for unrelated tokens are ignored, so
     * the collection may be the whole table or a pre-filtered slice: the result is the same.
     */
    public static AltGroup of(UUID account, Collection<IpAssociation> associations) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(associations, "associations");
        Set<String> ownTokens = associations.stream()
                .filter(link -> link.account().equals(account))
                .map(IpAssociation::ipToken)
                .collect(Collectors.toUnmodifiableSet());
        Set<UUID> alts = associations.stream()
                .filter(link -> ownTokens.contains(link.ipToken()))
                .map(IpAssociation::account)
                .filter(other -> !other.equals(account))
                .collect(Collectors.toUnmodifiableSet());
        return new AltGroup(account, alts);
    }
}
