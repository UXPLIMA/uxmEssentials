package com.uxplima.uxmessentials.shared.display;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A pure, recursive model of "show this display element only when …". A condition is parsed once from operator
 * config (see {@link ConditionParser}) and then evaluated per viewer against a {@link ConditionContext} via
 * {@link #matches(ConditionContext)}. The model is value-object only (no Bukkit, no rendering) so it lives in
 * {@code :core} and is reused by the scoreboard, tablist, and nametags modules alike. It mirrors vote's
 * {@code RewardSpec}/{@code RewardContext} split: the condition is the pure spec, the context supplies runtime
 * values through predicates and functions.
 *
 * <p>The hierarchy is {@code sealed} so every kind is enumerated and exhaustively handled. The recursive kinds
 * ({@link Negate}, {@link All}, {@link Any}) wrap inner conditions, which is why a sealed interface with record
 * variants reads cleaner here than a flat kind-enum would.
 *
 * <h2>Comparison semantics</h2>
 *
 * {@link Compare} expands <em>both</em> operands through {@link ConditionContext#resolvePlaceholders()} first,
 * then: if both expansions parse as numbers it compares numerically; otherwise it compares as strings, where
 * {@code =}/{@code !=} are string equality, {@code contains} is a substring test, and the ordering operators
 * ({@code >}, {@code >=}, {@code <}, {@code <=}) yield {@code false} on non-numeric operands rather than
 * imposing a lexicographic order the operator did not ask for.
 */
public sealed interface DisplayCondition
        permits DisplayCondition.Always,
                DisplayCondition.Never,
                DisplayCondition.Permission,
                DisplayCondition.World,
                DisplayCondition.Gamemode,
                DisplayCondition.Compare,
                DisplayCondition.Negate,
                DisplayCondition.All,
                DisplayCondition.Any {

    /** True when this condition is satisfied for the viewer described by {@code ctx}. */
    boolean matches(ConditionContext ctx);

    /** The unconditional element: always shown. This is the empty/blank condition. */
    static DisplayCondition always() {
        return Always.INSTANCE;
    }

    /**
     * The never-satisfied element: never shown. Returned by {@link ConditionParser} for an unparseable
     * condition so a broken filter hides its element rather than always showing it.
     */
    static DisplayCondition never() {
        return Never.INSTANCE;
    }

    /** Always-true. */
    record Always() implements DisplayCondition {
        static final Always INSTANCE = new Always();

        @Override
        public boolean matches(ConditionContext ctx) {
            Objects.requireNonNull(ctx, "ctx");
            return true;
        }
    }

    /** Always-false. The fail-safe result for an unparseable condition. */
    record Never() implements DisplayCondition {
        static final Never INSTANCE = new Never();

        @Override
        public boolean matches(ConditionContext ctx) {
            Objects.requireNonNull(ctx, "ctx");
            return false;
        }
    }

    /** True when the viewer holds the permission {@code node}. */
    record Permission(String node) implements DisplayCondition {
        public Permission {
            Objects.requireNonNull(node, "node");
        }

        @Override
        public boolean matches(ConditionContext ctx) {
            Objects.requireNonNull(ctx, "ctx");
            return ctx.hasPermission().test(node);
        }
    }

    /** True when the viewer's world equals {@code name} (case-insensitive). */
    record World(String name) implements DisplayCondition {
        public World {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public boolean matches(ConditionContext ctx) {
            Objects.requireNonNull(ctx, "ctx");
            return name.equalsIgnoreCase(ctx.worldName());
        }
    }

    /** True when the viewer's gamemode equals {@code mode} (case-insensitive). */
    record Gamemode(String mode) implements DisplayCondition {
        public Gamemode {
            Objects.requireNonNull(mode, "mode");
        }

        @Override
        public boolean matches(ConditionContext ctx) {
            Objects.requireNonNull(ctx, "ctx");
            return mode.equalsIgnoreCase(ctx.gamemode());
        }
    }

    /** The comparison operators a {@link Compare} understands. */
    enum Operator {
        EQUALS,
        NOT_EQUALS,
        GREATER_OR_EQUAL,
        LESS_OR_EQUAL,
        GREATER,
        LESS,
        CONTAINS
    }

    /**
     * Compares two operands after expanding both through the context's placeholder bridge. Numeric when both
     * sides expand to numbers, otherwise string-based; ordering operators on non-numeric operands are
     * {@code false} (see the interface docs).
     */
    record Compare(String left, Operator op, String right) implements DisplayCondition {
        public Compare {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(op, "op");
            Objects.requireNonNull(right, "right");
        }

        @Override
        public boolean matches(ConditionContext ctx) {
            Objects.requireNonNull(ctx, "ctx");
            String l = ctx.resolvePlaceholders().apply(left);
            String r = ctx.resolvePlaceholders().apply(right);
            l = l == null ? "" : l;
            r = r == null ? "" : r;
            OptionalDouble ln = parseNumber(l);
            OptionalDouble rn = parseNumber(r);
            if (ln.isPresent() && rn.isPresent()) {
                return compareNumeric(ln.getAsDouble(), rn.getAsDouble());
            }
            return compareString(l, r);
        }

        private boolean compareNumeric(double l, double r) {
            return switch (op) {
                case EQUALS -> l == r;
                case NOT_EQUALS -> l != r;
                case GREATER_OR_EQUAL -> l >= r;
                case LESS_OR_EQUAL -> l <= r;
                case GREATER -> l > r;
                case LESS -> l < r;
                case CONTAINS -> Double.toString(l).contains(Double.toString(r));
            };
        }

        private boolean compareString(String l, String r) {
            return switch (op) {
                case EQUALS -> l.equals(r);
                case NOT_EQUALS -> !l.equals(r);
                case CONTAINS -> l.contains(r);
                case GREATER_OR_EQUAL, LESS_OR_EQUAL, GREATER, LESS -> false;
            };
        }

        private static OptionalDouble parseNumber(String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return OptionalDouble.empty();
            }
            try {
                return OptionalDouble.of(Double.parseDouble(t));
            } catch (NumberFormatException ignored) {
                return OptionalDouble.empty();
            }
        }
    }

    /** True when {@code inner} is false. */
    record Negate(DisplayCondition inner) implements DisplayCondition {
        public Negate {
            Objects.requireNonNull(inner, "inner");
        }

        @Override
        public boolean matches(ConditionContext ctx) {
            Objects.requireNonNull(ctx, "ctx");
            return !inner.matches(ctx);
        }
    }

    /** True when every member condition is true (an empty list is vacuously true). */
    record All(List<DisplayCondition> conditions) implements DisplayCondition {
        public All {
            Objects.requireNonNull(conditions, "conditions");
            conditions = List.copyOf(conditions);
        }

        @Override
        public boolean matches(ConditionContext ctx) {
            Objects.requireNonNull(ctx, "ctx");
            return conditions.stream().allMatch(c -> c.matches(ctx));
        }
    }

    /** True when any member condition is true (an empty list is vacuously false). */
    record Any(List<DisplayCondition> conditions) implements DisplayCondition {
        public Any {
            Objects.requireNonNull(conditions, "conditions");
            conditions = List.copyOf(conditions);
        }

        @Override
        public boolean matches(ConditionContext ctx) {
            Objects.requireNonNull(ctx, "ctx");
            return conditions.stream().anyMatch(c -> c.matches(ctx));
        }
    }
}
