package com.uxplima.uxmessentials.shared.application.port;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A persistent, per-player key→value store backed by the plugin database. The menu engine's substrate for
 * arbitrary player-scoped data that must survive a restart or a world rollback (the Phase-2 data actions
 * {@code SET/ADD/SUB/MUL/DIV} write through it, the Phase-6 {@code %..._value_<key>%} placeholders read it).
 *
 * <p>Values are stored as plain strings; {@link #number} and {@link #apply} layer a parse-or-fallback numeric
 * view on top so a key can be treated as a counter without the caller hand-parsing. The store is deliberately
 * untyped and unbounded, operators name their own keys, which is why it is a database table rather than the
 * per-holder PDC the {@code PlayerMeta} accessor uses (PDC is transient per-holder state; this is durable, server
 * authoritative data).
 *
 * <h2>Threading</h2>
 * Reads ({@link #get}, {@link #number}, {@link #all}) are entity-thread-safe cache hits: a player's rows are
 * warmed into memory on join and dropped on quit, so a menu click never blocks the tick thread on the database.
 * Writes mutate that cache synchronously and persist asynchronously, so a write also returns to the caller
 * without touching the database on the calling thread.
 */
public interface PlayerDataStore {

    /** The arithmetic a {@link #apply} performs against the stored value. */
    enum NumericOp {

        /** Replace the stored value with the operand, ignoring whatever is there. */
        SET,

        /** Add the operand to the current value (a missing or non-numeric value reads as zero). */
        ADD,

        /** Subtract the operand from the current value. */
        SUB,

        /** Multiply the current value by the operand. */
        MUL,

        /**
         * Divide the current value by the operand. Dividing by zero is a documented no-change: the stored value
         * is left untouched and the current value is returned, so a misconfigured menu never throws.
         */
        DIV
    }

    /** The raw stored value for {@code key}, or empty when the player has no such key. */
    Optional<String> get(UUID player, String key);

    /** The stored value parsed as a {@code double}, or {@code fallback} when it is missing or not a number. */
    double number(UUID player, String key, double fallback);

    /** Store {@code value} under {@code key}, overwriting any previous value. */
    void set(UUID player, String key, String value);

    /**
     * Apply {@code op} against the stored value (treated as zero when missing or non-numeric) and store the
     * result, returning the new value. {@code SET} ignores the current value; dividing by zero changes nothing
     * and returns the current value rather than throwing. The read-modify-write is atomic per {@code (player, key)}.
     */
    double apply(UUID player, String key, NumericOp op, double operand);

    /** Remove {@code key} for {@code player}; a no-op when it is absent. */
    void remove(UUID player, String key);

    /** Every stored {@code key→value} for {@code player} as an immutable snapshot (empty when none). */
    Map<String, String> all(UUID player);
}
