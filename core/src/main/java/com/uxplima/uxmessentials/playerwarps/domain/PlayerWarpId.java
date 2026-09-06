package com.uxplima.uxmessentials.playerwarps.domain;

/**
 * The surrogate primary key of a persisted player-warp: a positive, database-assigned {@code long} that is
 * stable for the warp's whole lifetime and never reused. Names may be renamed and are globally unique only at
 * a point in time; the id is the durable identity that other rows (whitelist entries, ratings, rent ledgers)
 * reference.
 *
 * <p>A warp that has not been saved yet has no id. That absence is modelled on the aggregate as an
 * {@code Optional<PlayerWarpId>}, not as a zero or negative sentinel here. This type only ever holds a real,
 * positive key.
 *
 * @param value the positive database key
 */
public record PlayerWarpId(long value) {

    public PlayerWarpId {
        if (value <= 0) {
            throw new IllegalArgumentException("player warp id must be positive: " + value);
        }
    }

    /** Wrap a database-assigned key; rejects any non-positive value. */
    public static PlayerWarpId of(long value) {
        return new PlayerWarpId(value);
    }
}
