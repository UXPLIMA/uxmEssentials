package com.uxplima.uxmessentials.playerstate.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * A decision for {@code /exp}: how a player's experience should change, expressed in the domain so the adapter
 * only ever computes a clamped non-negative target from the player's current total. The operation pairs with a
 * unit ({@link Unit#POINTS} acts on the raw experience-point total, {@link Unit#LEVELS} on whole levels) so
 * {@code /exp give 30} and {@code /exp set 30 levels} read naturally while the maths stays here.
 *
 * <p>{@link #resolve(long)} turns the operation into the new total against a current total: {@code SET} replaces
 * it, {@code GIVE}/{@code TAKE} add or subtract, {@code RESET} zeroes it. The result is floored at zero so a
 * take larger than the balance empties it rather than going negative; the amount itself is validated
 * non-negative at construction so a malformed argument can never reach the live player.
 *
 * @param op which way the total moves
 * @param unit whether {@code amount} counts points or levels
 * @param amount the non-negative magnitude, ignored for {@link Op#RESET}
 */
public record ExperienceChange(Op op, Unit unit, long amount) {

    /** How the experience total moves. */
    public enum Op {
        GET,
        SET,
        GIVE,
        TAKE,
        RESET
    }

    /** Whether an amount is counted in raw experience points or in whole levels. */
    public enum Unit {
        POINTS,
        LEVELS
    }

    public ExperienceChange {
        if (op == null) {
            throw new IllegalArgumentException("op");
        }
        if (unit == null) {
            throw new IllegalArgumentException("unit");
        }
        if (amount < 0L) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
    }

    /** A read-only query that reports the current total without changing it. */
    public static ExperienceChange get() {
        return new ExperienceChange(Op.GET, Unit.POINTS, 0L);
    }

    /** A reset to zero experience. */
    public static ExperienceChange reset() {
        return new ExperienceChange(Op.RESET, Unit.POINTS, 0L);
    }

    /** True when the operation changes the total; {@link Op#GET} alone does not. */
    public boolean mutates() {
        return op != Op.GET;
    }

    /**
     * The new total this operation yields against {@code current}, floored at zero. {@code current} and the
     * result are in this change's {@link #unit}; the adapter maps that unit to and from the live player.
     */
    public long resolve(long current) {
        long next =
                switch (op) {
                    case GET -> current;
                    case SET -> amount;
                    case GIVE -> current + amount;
                    case TAKE -> current - amount;
                    case RESET -> 0L;
                };
        return Math.max(0L, next);
    }

    /** Parse the operation keyword ({@code get}/{@code set}/{@code give}/{@code take}/{@code reset}). */
    public static Optional<Op> parseOp(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "get" -> Optional.of(Op.GET);
            case "set" -> Optional.of(Op.SET);
            case "give", "add" -> Optional.of(Op.GIVE);
            case "take", "remove" -> Optional.of(Op.TAKE);
            case "reset", "clear" -> Optional.of(Op.RESET);
            default -> Optional.empty();
        };
    }
}
