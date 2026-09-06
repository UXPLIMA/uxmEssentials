package com.uxplima.uxmessentials.ranks.domain;

import java.util.Objects;

/**
 * Identity of a single rank on the ladder. The stable key an operator names the rank by in {@code ranks.conf}
 * ({@code first}, {@code citizen}, {@code vip}) and the value stored in the {@code player_ranks.rank_id} column.
 * It is the rank's natural key, so a stored pointer resolves back to a {@link Rank} without a surrogate id.
 *
 * <p>The value is the config section name, so it is trimmed and required non-blank at construction; a blank id
 * would neither address a config section nor round-trip through the store.
 *
 * @param value the rank's stable identifier
 */
public record RankId(String value) {

    public RankId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("rank id must not be blank");
        }
    }

    /** Factory mirroring the canonical constructor, for readability at call sites. */
    public static RankId of(String value) {
        return new RankId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
