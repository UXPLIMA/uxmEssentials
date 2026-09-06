package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code %argument_<name>%} tokens inside an action ref's argument values from the arguments a menu was
 * opened with. Rendered menu TEXT already gets this through
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer}; this is the same rule for
 * the ACTION side, so a menu opened with {@code amount=5} whose confirm button runs {@code give-money:%argument_amount%}
 * actually gives five. It deliberately touches only the {@code argument_} tokens and leaves every other
 * {@code %token%} verbatim, so the downstream registry / PlaceholderAPI / MiniMessage handling is unchanged.
 *
 * <p>Pure and allocation-conscious: when the open carried no arguments there is nothing to expand, so the original
 * map is returned unchanged rather than copied: the common case (a menu opened without a command) pays nothing.
 *
 * <p>Public because two engine paths in different packages reach it: the click path ({@code MenuListener}, this
 * package) and the open path ({@code Menus}, the parent package) both expand an action ref's arguments the same way
 * before dispatching an open-action. It is a pure static helper with no state, so widening it leaks nothing.
 */
public final class ActionArguments {

    /** A single {@code %token%} placeholder; {@code group(1)} is the bare token name. Mirrors the renderer's pattern. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\w+)%");

    /** Prefix marking a token as a typed command argument resolved from the open arguments rather than the registry. */
    private static final String ARGUMENT_PREFIX = "argument_";

    private ActionArguments() {}

    /**
     * The action arguments with every {@code %argument_<name>%} token in a value replaced by the matching open
     * argument (an unknown name yields empty, matching the renderer). Returns {@code args} unchanged when
     * {@code arguments} is empty, the identity fast-path, so a menu opened without a command allocates nothing.
     */
    public static Map<String, String> resolve(Map<String, String> args, Map<String, String> arguments) {
        if (arguments.isEmpty()) {
            return args;
        }
        Map<String, String> resolved = new LinkedHashMap<>(args.size());
        args.forEach((key, value) -> resolved.put(key, substitute(value, arguments)));
        return resolved;
    }

    /** Replace only the {@code argument_}-prefixed tokens in {@code value}, leaving every other {@code %token%} verbatim. */
    private static String substitute(String value, Map<String, String> arguments) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = token.startsWith(ARGUMENT_PREFIX)
                    ? arguments.getOrDefault(token.substring(ARGUMENT_PREFIX.length()), "")
                    : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * The action arguments with every bare {@code %name%} token that {@code locals} defines replaced by its value
     * the same menu-local placeholders (the open spec's {@code placeholders {}} block, plus the item-drag flow's
     * {@code %drag_material%}/{@code %drag_amount%}/{@code %drag_name%}) that the renderer already expands in text.
     * A token the map does not define is left verbatim, so the downstream registry / PlaceholderAPI / MiniMessage
     * handling is unchanged. Returns {@code args} unchanged when {@code locals} is empty, the identity fast-path, so
     * a menu without a local block or a drag exposure allocates nothing and dispatches byte-identically.
     */
    public static Map<String, String> resolveLocals(Map<String, String> args, Map<String, String> locals) {
        if (locals.isEmpty()) {
            return args;
        }
        Map<String, String> resolved = new LinkedHashMap<>(args.size());
        args.forEach((key, value) -> resolved.put(key, substituteLocals(value, locals)));
        return resolved;
    }

    /** Replace each {@code %name%} token in {@code value} that {@code locals} defines, leaving every other token verbatim. */
    private static String substituteLocals(String value, Map<String, String> locals) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement = locals.getOrDefault(matcher.group(1), matcher.group());
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
