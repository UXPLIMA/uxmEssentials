package com.uxplima.uxmessentials.trade.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a single trade session: a surrogate {@link UUID} minted when two players open a trade.
 *
 * <p>A trade has no natural key: the same pair of players may trade many times, so the session is addressed by a
 * generated id rather than by its participants. The id travels with the session across a region hop and, in the
 * cross-server phase, over the bus and into the escrow table, so the two backend servers agree on which exchange a
 * message refers to.
 *
 * @param value the session's stable identifier
 */
public record TradeId(UUID value) {

    public TradeId {
        Objects.requireNonNull(value, "value");
    }

    /** A fresh, random trade id for a newly opened session. */
    public static TradeId newId() {
        return new TradeId(UUID.randomUUID());
    }

    /** The id parsed from its canonical string form, for rehydrating a session from the bus or the escrow table. */
    public static TradeId fromString(String value) {
        Objects.requireNonNull(value, "value");
        return new TradeId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
