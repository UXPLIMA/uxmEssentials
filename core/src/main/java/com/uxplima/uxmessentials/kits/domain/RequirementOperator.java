package com.uxplima.uxmessentials.kits.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The comparison a {@link KitRequirement} applies between its two operands. Each constant carries the token an
 * operator writes in a kit's {@code requirements} list ({@code >=}, {@code !=}, …); {@link #parse(String)} maps
 * a raw token back to the constant so the codec never branches on the symbol itself.
 *
 * <p>The ordered comparisons ({@link #GTE}, {@link #LTE}, {@link #GT}, {@link #LT}) are only meaningful when
 * both resolved operands are numbers; {@link #EQ} and {@link #NEQ} also apply to plain strings. Which of the
 * two an evaluator may use against a given pair of values is the evaluator's call. The domain only models the
 * operator, it never resolves a placeholder or performs the comparison itself, keeping the kernel free of any
 * PlaceholderAPI dependency.
 */
public enum RequirementOperator {

    /** Equality: numeric when both sides parse as numbers, otherwise a string match. */
    EQ("=="),

    /** Inequality: the negation of {@link #EQ}, equally valid for strings. */
    NEQ("!="),

    /** Greater-than-or-equal: numeric only. */
    GTE(">="),

    /** Less-than-or-equal: numeric only. */
    LTE("<="),

    /** Strictly greater-than: numeric only. */
    GT(">"),

    /** Strictly less-than: numeric only. */
    LT("<");

    private final String token;

    RequirementOperator(String token) {
        this.token = token;
    }

    /** The token this operator is written as in a kit's {@code requirements} list. */
    public String token() {
        return token;
    }

    /** True when this operator is an ordered comparison ({@code >= <= > <}), valid only between numbers. */
    public boolean isOrdered() {
        return this == GTE || this == LTE || this == GT || this == LT;
    }

    /**
     * Parse a raw operator token into its constant, tolerating surrounding spaces, or empty when the token is
     * not one of {@code == != >= <= > <}. The two-character tokens are tried before the single-character ones
     * so {@code >=} never mis-parses as {@code >}.
     */
    public static Optional<RequirementOperator> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String token = raw.strip();
        for (RequirementOperator op : values()) {
            if (op.token.equals(token)) {
                return Optional.of(op);
            }
        }
        return Optional.empty();
    }
}
