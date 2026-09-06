package com.uxplima.uxmessentials.shared.domain;

/**
 * The empty success value. The type a {@link Result} carries when an operation succeeds but produces
 * nothing meaningful to return (a stamp written, a cooldown gate passed). It exists so a void-shaped
 * outcome can still flow through {@code Result<Unit, E>} and be pattern-matched like any other.
 *
 * <p>A single canonical {@link #INSTANCE} is shared; the type is otherwise valueless.
 */
public enum Unit {
    INSTANCE
}
