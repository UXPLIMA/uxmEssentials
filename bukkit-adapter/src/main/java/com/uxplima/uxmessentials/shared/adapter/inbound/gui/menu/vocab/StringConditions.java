package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The string slice of the menu condition vocabulary: gates that compare placeholder-expanded text. Where the
 * requirement pack asks what the viewer holds (money, an item, a free slot) and {@code papi-compare} weighs two
 * numeric operands, these ask about the shape of a string a spec has already resolved, does it contain a needle,
 * match a pattern, have a certain length, look like a number. Registered once at startup into the shared
 * {@link MenuBindings} alongside the generic {@link MenuVocabulary} conditions, so a disk-loaded spec resolves
 * {@code contains} / {@code regex} / {@code length} the same way a code-registered feature menu does.
 *
 * <p>A valued condition is written {@code contains:%player_name% Steve} in config; the parser leaves that whole (it
 * is registry blind), and the runtime's registry-aware split re-keys it to the head {@code contains} with
 * {@code %player_name% Steve} carried as {@code value}. Every handler here reads its argument from {@code value},
 * expands the {@code %token%}s in its operands against the placeholder registry, then compares the resolved text
 * so {@code %player_name%} is the live viewer name by the time the comparison runs.
 *
 * <p><strong>Every condition fails closed.</strong> A gate that cannot be evaluated must not pass: a missing
 * operand, a non-integer bound, a malformed regex, or anything a comparison unexpectedly throws all answer
 * {@code false} through the {@link #closed} wrapper, better to hide an item or deny a click than to grant on text
 * we could not read.
 *
 * <p>Threading: these are pure string work with no Bukkit or foreign-player access, so they are Folia-safe wherever
 * the engine runs them (a {@code view} scan during render, a click gate on the viewer's entity thread). Nothing here
 * produces player-facing text, a condition returns a boolean, so no {@code MessageKey} is involved; the only text
 * is a diagnostic {@code log} line when a comparison throws.
 */
public final class StringConditions {

    /** Matches a {@code %token%} placeholder in an operand; {@code group(1)} is the bare id the registry resolves. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");

    /** Splits an argument on runs of whitespace; the input is stripped first so no empty leading/trailing token slips in. */
    private static final Splitter WHITESPACE = Splitter.on(Pattern.compile("\\s+"));

    private StringConditions() {}

    /**
     * Register the string conditions into {@code bindings}. The placeholder registry the operands expand through is
     * read from {@code bindings} itself, so no separate handle is threaded; {@code log} is the operator console
     * logger a fail-closed condition warns through when a comparison throws. Left separate from
     * {@link MenuVocabulary#registerConditions} so that method's existing call-sites stay untouched, the composition
     * root calls both.
     */
    public static void register(MenuBindings bindings, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(log, "log");
        PlaceholderRegistry placeholders = bindings.placeholders();
        bindings.condition("contains", closed("contains", log, (ctx, args) -> contains(ctx, args, placeholders)));
        bindings.condition(
                "equals-ignorecase",
                closed("equals-ignorecase", log, (ctx, args) -> equalsIgnoreCase(ctx, args, placeholders)));
        bindings.condition("regex", closed("regex", log, (ctx, args) -> regex(ctx, args, placeholders)));
        bindings.condition("length", closed("length", log, (ctx, args) -> length(ctx, args, placeholders)));
        bindings.condition("is-integer", closed("is-integer", log, (ctx, args) -> isInteger(ctx, args, placeholders)));
        bindings.condition("is-double", closed("is-double", log, (ctx, args) -> isDouble(ctx, args, placeholders)));
        bindings.condition("is-object", closed("is-object", log, (ctx, args) -> isObject(ctx, args, placeholders)));
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a {@code false}
     * result rather than escaping into the render or click path. A condition that cannot be evaluated must fail
     * closed, the same discipline the requirement pack applies. A malformed {@code regex} pattern reaches here as a
     * {@code PatternSyntaxException} and is caught the same way.
     */
    private static BiPredicate<MenuContext, Map<String, String>> closed(
            String condition, Logger log, BiPredicate<MenuContext, Map<String, String>> body) {
        return (ctx, args) -> {
            try {
                return body.test(ctx, args);
            } catch (RuntimeException failure) {
                log.warn("event=menu_string_condition_failed condition={} value={}", condition, value(args));
                return false;
            }
        };
    }

    /** {@code contains:<A> <B>}: expand both operands; true iff A contains B. A missing B is {@code false}. */
    private static boolean contains(MenuContext ctx, Map<String, String> args, PlaceholderRegistry placeholders) {
        String[] operands = splitFirst(value(args));
        if (operands.length < 2) {
            return false;
        }
        return expand(operands[0], ctx, placeholders).contains(expand(operands[1], ctx, placeholders));
    }

    /** {@code equals-ignorecase:<A> <B>}: expand both operands; true iff A equals B ignoring case. */
    private static boolean equalsIgnoreCase(
            MenuContext ctx, Map<String, String> args, PlaceholderRegistry placeholders) {
        String[] operands = splitFirst(value(args));
        if (operands.length < 2) {
            return false;
        }
        return expand(operands[0], ctx, placeholders).equalsIgnoreCase(expand(operands[1], ctx, placeholders));
    }

    /**
     * {@code regex:<A> <pattern>}, expand A, then require the whole of A to match {@code pattern}. The pattern is a
     * literal regex and is <em>not</em> expanded: expanding it would corrupt escapes, and a {@code %token%} inside a
     * pattern is unusual. A malformed pattern throws {@link java.util.regex.PatternSyntaxException} up to
     * {@link #closed}, which is the fail-closed path.
     */
    private static boolean regex(MenuContext ctx, Map<String, String> args, PlaceholderRegistry placeholders) {
        String[] operands = splitFirst(value(args));
        if (operands.length < 2) {
            return false;
        }
        String subject = expand(operands[0], ctx, placeholders);
        return Pattern.compile(operands[1]).matcher(subject).matches();
    }

    /**
     * {@code length:<A> <op> <n>}: exactly three whitespace tokens. Expand A, then compare its length against the
     * integer {@code n} with {@code op} (one of {@code > >= < <= == = !=}). A wrong token count, a non-integer
     * {@code n}, or an unknown operator is {@code false}.
     */
    private static boolean length(MenuContext ctx, Map<String, String> args, PlaceholderRegistry placeholders) {
        List<String> parts = WHITESPACE.splitToList(value(args).strip());
        if (parts.size() != 3) {
            return false;
        }
        OptionalInt bound = parseInt(parts.get(2));
        if (bound.isEmpty()) {
            return false;
        }
        int length = expand(parts.get(0), ctx, placeholders).length();
        return compareLength(length, parts.get(1), bound.getAsInt());
    }

    /** {@code is-integer:<A>}: expand A; true iff it parses as a Java {@code int}. */
    private static boolean isInteger(MenuContext ctx, Map<String, String> args, PlaceholderRegistry placeholders) {
        String operand = expand(value(args).strip(), ctx, placeholders);
        try {
            Integer.parseInt(operand);
            return true;
        } catch (NumberFormatException notAnInteger) {
            return false;
        }
    }

    /** {@code is-double:<A>}: expand A; true iff it parses as a finite {@code double} (so {@code Infinity}/NaN fail). */
    private static boolean isDouble(MenuContext ctx, Map<String, String> args, PlaceholderRegistry placeholders) {
        String operand = expand(value(args).strip(), ctx, placeholders);
        try {
            return Double.isFinite(Double.parseDouble(operand));
        } catch (NumberFormatException notADouble) {
            return false;
        }
    }

    /** {@code is-object:<A>}: expand A; true iff it is present (non-blank), DeluxeMenus' "is object" sense. */
    private static boolean isObject(MenuContext ctx, Map<String, String> args, PlaceholderRegistry placeholders) {
        return !expand(value(args).strip(), ctx, placeholders).isBlank();
    }

    /** Apply a relational/equality {@code op} to a string length and a bound; an unknown operator is {@code false}. */
    private static boolean compareLength(int length, String op, int bound) {
        return switch (op) {
            case ">" -> length > bound;
            case ">=" -> length >= bound;
            case "<" -> length < bound;
            case "<=" -> length <= bound;
            case "==", "=" -> length == bound;
            case "!=" -> length != bound;
            default -> false;
        };
    }

    /**
     * Split {@code value} on the first run of whitespace into the leading operand and the remainder, stripping first
     * so no empty leading token slips in. The remainder (operand B) keeps any interior spaces, an
     * {@code equals-ignorecase} target or a regex pattern may itself contain them. A value with no whitespace yields
     * a single-element array, the signal that operand B is missing.
     */
    private static String[] splitFirst(String value) {
        return value.strip().split("\\s+", 2);
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

    /** The whole number in {@code token}, or empty when it is blank or not an integer. */
    private static OptionalInt parseInt(String token) {
        try {
            return OptionalInt.of(Integer.parseInt(token.strip()));
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }
}
