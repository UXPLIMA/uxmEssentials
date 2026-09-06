package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The numeric and spatial slice of the menu condition vocabulary. Two capabilities the other packs left just out of
 * reach: an explicit {@code compare:<A> <op> <B>} that weighs two placeholder-expanded operands (the same numeric-or-
 * string comparison {@code papi-compare} performs, but reachable from a plain {@code id:value} token, the loader
 * cannot fill {@code papi-compare}'s three named {@code left}/{@code op}/{@code right} args from config), and a set of
 * location gates ({@code is-near}, {@code cuboid}, {@code world}) that ask <em>where the viewer is standing</em>. The
 * general numeric comparators ({@code > >= == != <= <}) and arithmetic are already reachable through the {@code expr}
 * condition, which expands its {@code %token%}s then runs the sandboxed evaluator; this pack adds the explicit
 * config-form comparison and the spatial checks that no evaluator can express.
 *
 * <p>Registered once at startup into the shared {@link MenuBindings} alongside the generic {@link MenuVocabulary}
 * conditions, so a disk-loaded spec resolves {@code compare} / {@code is-near} / {@code world} the same way a
 * code-registered feature menu does. A valued condition is written {@code world:nether} or {@code is-near:0 64 0 8} in
 * config; the parser leaves that whole (it is registry-blind), and the runtime's registry-aware split re-keys it to
 * the registered head ({@code world}, {@code is-near}) with the remainder carried as {@code value}. So every handler
 * here reads its argument from {@code value}, exactly as an action does.
 *
 * <p><strong>Every condition fails closed.</strong> A gate that cannot be evaluated must not pass: an offline viewer,
 * a wrong token count, a non-numeric coordinate, an unknown operator, or anything a lookup unexpectedly throws all
 * answer {@code false} through the {@link #closed} wrapper, better to hide an item or deny a click than to grant on a
 * value we could not read. The viewer's live {@link Player} is resolved from the open context's UUID; a viewer who
 * logged off in the gap resolves to {@code null} and the spatial conditions are {@code false}.
 *
 * <p>Threading: the spatial gates read only the viewer's <em>own</em> location and world on the render/entity thread
 * that runs them (a {@code view} scan during the renderer's populate, a click gate on the viewer's entity thread),
 * which is Folia-safe. No foreign entity is touched and the online roster is never enumerated, so no Folia allowlist
 * entry is needed. {@code compare} is pure string/number work. Nothing here produces player-facing text (a condition
 * returns a boolean), so no {@code MessageKey} is involved; the only text is a diagnostic {@code log} line when an
 * evaluation throws.
 */
public final class NumericSpatialConditions {

    /** Matches a {@code %token%} placeholder in a {@code compare} operand; {@code group(1)} is the bare id. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");

    /** Splits an argument on runs of whitespace; the input is stripped first so no empty leading/trailing token slips in. */
    private static final Splitter WHITESPACE = Splitter.on(Pattern.compile("\\s+"));

    private NumericSpatialConditions() {}

    /**
     * Register the numeric and spatial conditions into {@code bindings}. The placeholder registry {@code compare}'s
     * operands expand through is read from {@code bindings} itself, so no separate handle is threaded; {@code log} is
     * the operator console logger a fail-closed condition warns through when an evaluation throws. Left separate from
     * {@link MenuVocabulary#registerConditions} so that method's existing call-sites stay untouched, the composition
     * root calls both.
     */
    public static void register(MenuBindings bindings, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(log, "log");
        PlaceholderRegistry placeholders = bindings.placeholders();
        bindings.condition("compare", closed("compare", log, (ctx, args) -> compare(ctx, args, placeholders)));
        bindings.condition("is-near", closed("is-near", log, NumericSpatialConditions::isNear));
        bindings.condition("cuboid", closed("cuboid", log, NumericSpatialConditions::cuboid));
        bindings.condition("world", closed("world", log, NumericSpatialConditions::world));
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a {@code false}
     * result rather than escaping into the render or click path. A condition that cannot be evaluated must fail
     * closed, the same discipline the string and requirement packs apply.
     */
    private static BiPredicate<MenuContext, Map<String, String>> closed(
            String condition, Logger log, BiPredicate<MenuContext, Map<String, String>> body) {
        return (ctx, args) -> {
            try {
                return body.test(ctx, args);
            } catch (RuntimeException failure) {
                log.warn("event=menu_numeric_spatial_condition_failed condition={} value={}", condition, value(args));
                return false;
            }
        };
    }

    /**
     * {@code compare:<A> <op> <B>}: exactly three whitespace tokens. Expand A and B, then compare them: when both
     * resolve to numbers the comparison is numeric across the full operator set ({@code = == != < > <= >=}), otherwise
     * only equality is meaningful and falls back to a string test ({@code =}/{@code ==} equals, {@code !=} not-equals,
     * any other operator on non-numbers is {@code false}). A wrong token count is {@code false}. This is the
     * config-reachable twin of {@code papi-compare}, whose named {@code left}/{@code op}/{@code right} args the loader
     * cannot fill from a plain token.
     */
    private static boolean compare(MenuContext ctx, Map<String, String> args, PlaceholderRegistry placeholders) {
        List<String> tokens = WHITESPACE.splitToList(value(args).strip());
        if (tokens.size() != 3) {
            return false;
        }
        String left = expand(tokens.get(0), ctx, placeholders);
        String right = expand(tokens.get(2), ctx, placeholders);
        return compareResolved(left, tokens.get(1), right);
    }

    /**
     * Compare two already-resolved operands with {@code op}: numeric when both parse as numbers, otherwise a string
     * equality test. Kept public and Bukkit-free so a plain-JUnit test can pin both branches; the condition itself
     * expands its {@code %token%}s before calling this.
     */
    public static boolean compareResolved(String left, String op, String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(op, "op");
        Objects.requireNonNull(right, "right");
        OptionalDouble leftNum = parseNumber(left);
        OptionalDouble rightNum = parseNumber(right);
        if (leftNum.isPresent() && rightNum.isPresent()) {
            return compareNumeric(leftNum.getAsDouble(), op.strip(), rightNum.getAsDouble());
        }
        return compareString(left, op.strip(), right);
    }

    /**
     * {@code is-near:<x> <y> <z> <radius>}: four whitespace tokens as numbers. True iff the viewer's current location
     * is within {@code radius} blocks of {@code (x,y,z)} in the viewer's current world. The comparison uses
     * {@link Location#distanceSquared} against {@code radius*radius} so no square root is taken; a negative radius is
     * {@code false} (an empty region), and a malformed coordinate is {@code false}.
     */
    private static boolean isNear(MenuContext ctx, Map<String, String> args) {
        Player player = viewer(ctx);
        double @Nullable [] coords = parseCoords(value(args), 4);
        if (player == null || coords == null) {
            return false;
        }
        Location at = player.getLocation();
        double radius = coords[3];
        if (at == null || radius < 0) {
            return false;
        }
        Location target = new Location(player.getWorld(), coords[0], coords[1], coords[2]);
        return at.distanceSquared(target) <= radius * radius;
    }

    /**
     * {@code cuboid:<x1> <y1> <z1> <x2> <y2> <z2>}: six whitespace tokens as numbers. True iff the viewer's current
     * location falls inside the axis-aligned box the two corners span (min/max taken per axis, both bounds inclusive),
     * in the viewer's current world. A malformed coordinate is {@code false}.
     */
    private static boolean cuboid(MenuContext ctx, Map<String, String> args) {
        Player player = viewer(ctx);
        double @Nullable [] c = parseCoords(value(args), 6);
        if (player == null || c == null) {
            return false;
        }
        Location at = player.getLocation();
        if (at == null) {
            return false;
        }
        return within(at.getX(), c[0], c[3]) && within(at.getY(), c[1], c[4]) && within(at.getZ(), c[2], c[5]);
    }

    /** {@code world:<name>}: true iff the viewer's current world name equals {@code name} ignoring case. Blank is false. */
    private static boolean world(MenuContext ctx, Map<String, String> args) {
        Player player = viewer(ctx);
        String name = value(args).strip();
        if (player == null || name.isEmpty()) {
            return false;
        }
        return player.getWorld().getName().equalsIgnoreCase(name);
    }

    /** Apply a relational/equality {@code op} to two numbers ({@code ==} treated as {@code =}); unknown operator is false. */
    private static boolean compareNumeric(double left, String op, double right) {
        return switch (op) {
            case "=", "==" -> left == right;
            case "!=" -> left != right;
            case "<" -> left < right;
            case ">" -> left > right;
            case "<=" -> left <= right;
            case ">=" -> left >= right;
            default -> false;
        };
    }

    /** Apply an equality {@code op} to two strings; only equality is meaningful on text, any other operator is false. */
    private static boolean compareString(String left, String op, String right) {
        return switch (op) {
            case "=", "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            default -> false;
        };
    }

    /** Whether {@code v} lies between {@code a} and {@code b} inclusive, in whichever order the two bounds are given. */
    private static boolean within(double v, double a, double b) {
        return v >= Math.min(a, b) && v <= Math.max(a, b);
    }

    /**
     * Parse exactly {@code count} whitespace tokens of {@code value} as numbers. Kept public and Bukkit-free so a
     * plain-JUnit test can pin the grammar; a wrong token count or any non-numeric token yields {@code null}, the
     * signal to fail closed.
     */
    public static double @Nullable [] parseCoords(String value, int count) {
        Objects.requireNonNull(value, "value");
        List<String> tokens = WHITESPACE.splitToList(value.strip());
        if (tokens.size() != count) {
            return null;
        }
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            OptionalDouble parsed = parseNumber(tokens.get(i));
            if (parsed.isEmpty()) {
                return null;
            }
            out[i] = parsed.getAsDouble();
        }
        return out;
    }

    /** The live {@link Player} for the open context's viewer, or {@code null} when that player is offline. */
    private static @Nullable Player viewer(MenuContext ctx) {
        return Bukkit.getPlayer(ctx.viewer().uuid());
    }

    /** Replace every {@code %token%} in {@code operand} with its resolved placeholder value (or empty when unknown). */
    private static String expand(String operand, MenuContext ctx, PlaceholderRegistry placeholders) {
        Matcher matcher = PLACEHOLDER.matcher(operand);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = placeholders.resolveOrReport(matcher.group(1), ctx).orElse("");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** The single positional argument the runtime's split carried onto the condition ref, or empty when it had none. */
    private static String value(Map<String, String> args) {
        return args.getOrDefault("value", "");
    }

    /** The token as a finite-or-not {@code double}, or empty when it is blank or not numeric. */
    private static OptionalDouble parseNumber(String token) {
        try {
            return OptionalDouble.of(Double.parseDouble(token.strip()));
        } catch (NumberFormatException notNumeric) {
            return OptionalDouble.empty();
        }
    }
}
