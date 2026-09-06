package com.uxplima.uxmessentials.shared.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Turns an operator-written condition string into a {@link DisplayCondition}. Tolerant by design: this parses
 * the small DSL the scoreboard/tablist/nametags modules expose to server admins, and admins make typos.
 *
 * <h2>Grammar</h2>
 *
 * <ul>
 *   <li>blank or {@code null} → {@link DisplayCondition#always()} (no filter, always show).</li>
 *   <li>a leading {@code !} → wrap the remainder in {@link DisplayCondition.Negate}.</li>
 *   <li>{@code permission:<node>} → {@link DisplayCondition.Permission}.</li>
 *   <li>{@code world:<name>} → {@link DisplayCondition.World}.</li>
 *   <li>{@code gamemode:<mode>} → {@link DisplayCondition.Gamemode}.</li>
 *   <li>{@code <left> <op> <right>} → {@link DisplayCondition.Compare}, where {@code op} is one of
 *       {@code =}, {@code !=}, {@code >=}, {@code <=}, {@code >}, {@code <}, or the word {@code contains}.</li>
 *   <li>a {@code &&}- or {@code ,}-separated list → {@link DisplayCondition.All}.</li>
 *   <li>a {@code ||}-separated list → {@link DisplayCondition.Any}.</li>
 * </ul>
 *
 * <h2>Fail-safe</h2>
 *
 * An <em>unparseable</em> condition resolves to {@link DisplayCondition#never()}, not {@code always()}. The
 * intent of writing a condition is "show this only if X"; if X cannot be understood, hiding the element is the
 * safe failure: it never surfaces an element the operator meant to gate behind a (broken) rule. A blank
 * condition is different: it is a deliberate "no filter" and maps to {@code always()}.
 */
public final class ConditionParser {

    private ConditionParser() {}

    /** Parses {@code raw} into a condition; see the class docs for the grammar and the fail-safe rule. */
    public static DisplayCondition parse(@Nullable String raw) {
        if (raw == null) {
            return DisplayCondition.always();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return DisplayCondition.always();
        }
        return parseExpression(trimmed);
    }

    private static DisplayCondition parseExpression(String expr) {
        // OR binds loosest, then AND, then a single atom (which may itself be negated).
        List<String> orParts = splitTopLevel(expr, "||");
        if (orParts.size() > 1) {
            return new DisplayCondition.Any(parseEach(orParts));
        }
        List<String> andParts = splitAnd(expr);
        if (andParts.size() > 1) {
            return new DisplayCondition.All(parseEach(andParts));
        }
        return parseAtom(expr.trim());
    }

    private static List<DisplayCondition> parseEach(List<String> parts) {
        List<DisplayCondition> out = new ArrayList<>(parts.size());
        for (String part : parts) {
            out.add(parseExpression(part.trim()));
        }
        return out;
    }

    private static DisplayCondition parseAtom(String atom) {
        if (atom.isEmpty()) {
            return DisplayCondition.never();
        }
        if (atom.startsWith("!")) {
            return new DisplayCondition.Negate(parseExpression(atom.substring(1).trim()));
        }
        DisplayCondition prefixed = parsePrefixed(atom);
        if (prefixed != null) {
            return prefixed;
        }
        DisplayCondition comparison = parseComparison(atom);
        if (comparison != null) {
            return comparison;
        }
        return DisplayCondition.never();
    }

    private static @Nullable DisplayCondition parsePrefixed(String atom) {
        int colon = atom.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String key = atom.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String value = atom.substring(colon + 1).trim();
        if (value.isEmpty()) {
            return null;
        }
        return switch (key) {
            case "permission", "perm" -> new DisplayCondition.Permission(value);
            case "world" -> new DisplayCondition.World(value);
            case "gamemode", "gm" -> new DisplayCondition.Gamemode(value);
            default -> null;
        };
    }

    private static @Nullable DisplayCondition parseComparison(String atom) {
        // Order matters: try the two-character operators before their one-character prefixes so ">=" is not
        // read as ">". The word operator "contains" is matched on word boundaries.
        for (OperatorToken token : OPERATOR_TOKENS) {
            int idx = token.indexIn(atom);
            if (idx < 0) {
                continue;
            }
            String left = atom.substring(0, idx).trim();
            String right = atom.substring(idx + token.length()).trim();
            if (left.isEmpty() || right.isEmpty()) {
                return null;
            }
            return new DisplayCondition.Compare(left, token.operator(), right);
        }
        return null;
    }

    private static List<String> splitAnd(String expr) {
        List<String> byAmp = splitTopLevel(expr, "&&");
        if (byAmp.size() > 1) {
            return byAmp;
        }
        return splitTopLevel(expr, ",");
    }

    private static List<String> splitTopLevel(String expr, String delimiter) {
        List<String> parts = new ArrayList<>();
        int from = 0;
        int idx;
        while ((idx = expr.indexOf(delimiter, from)) >= 0) {
            parts.add(expr.substring(from, idx));
            from = idx + delimiter.length();
        }
        parts.add(expr.substring(from));
        return parts;
    }

    /**
     * A comparison operator and how to find it in a string. Symbol operators are plain substring matches;
     * {@code contains} is matched only when it stands as a whitespace-surrounded word so it does not fire on a
     * value that merely embeds the letters. {@link #indexIn(String)} and {@link #length()} together report the
     * span of the operator within the original atom, so the caller can slice the operands off either side.
     */
    private record OperatorToken(String literal, DisplayCondition.Operator operator, boolean word) {

        int indexIn(String atom) {
            if (word) {
                int at = atom.indexOf(" " + literal + " ");
                // Point at the literal itself, past the leading space we required around it.
                return at < 0 ? -1 : at + 1;
            }
            return atom.indexOf(literal);
        }

        int length() {
            return literal.length();
        }
    }

    // Two-character operators precede their one-character prefixes; the word operator is last.
    private static final List<OperatorToken> OPERATOR_TOKENS = List.of(
            new OperatorToken("!=", DisplayCondition.Operator.NOT_EQUALS, false),
            new OperatorToken(">=", DisplayCondition.Operator.GREATER_OR_EQUAL, false),
            new OperatorToken("<=", DisplayCondition.Operator.LESS_OR_EQUAL, false),
            new OperatorToken("=", DisplayCondition.Operator.EQUALS, false),
            new OperatorToken(">", DisplayCondition.Operator.GREATER, false),
            new OperatorToken("<", DisplayCondition.Operator.LESS, false),
            new OperatorToken("contains", DisplayCondition.Operator.CONTAINS, true));
}
