package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.ExpressionException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.Expressions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;

/**
 * The generic action vocabulary every menu can lean on without a feature wiring it. Registered once at startup
 * into the shared {@link MenuBindings}, so a spec loaded from disk resolves {@code close} / {@code open} /
 * {@code command} / {@code message} / {@code sound} the same way a code-registered feature menu does. Each handler
 * runs as the clicking player. Nothing here touches the console; that is gated separately so an operator menu
 * cannot dispatch privileged commands by default.
 */
public final class MenuVocabulary {

    /** A {@code %token%} placeholder inside a {@code papi-compare} operand; {@code group(1)} is the bare id. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");

    /** Splits an {@code on-page} range spec on its commas; each token is a single page or an {@code A-B} range. */
    private static final Splitter COMMAS = Splitter.on(',');

    private MenuVocabulary() {}

    /**
     * Register the generic actions into {@code bindings}. {@code menus} is needed only by the {@code open} action,
     * which opens another registered spec for the same viewer with no subject. An operator-opened menu carries no
     * domain object. The {@code console} action is always registered so a spec referencing it still passes startup
     * validation, but it only dispatches when {@code allowConsole} is true; otherwise it no-ops and warns through
     * {@code log} so an operator who forgot the {@code custommenus.allow-console} flag sees why nothing happened.
     */
    public static void registerActions(MenuBindings bindings, Menus menus, boolean allowConsole, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(menus, "menus");
        Objects.requireNonNull(log, "log");
        bindings.action("close", ctx -> ctx.player().closeInventory());
        // `back` steps to the previous menu in the viewer's history (the engine holds the per-player stack); from the
        // first/root menu, or on an engine wired without a history tracker, there is nothing beneath, so it closes.
        bindings.action("back", ctx -> menus.back(ctx.viewer()));
        bindings.action("open", ctx -> {
            OpenTarget target = OpenTarget.parse(ctx.arg());
            menus.open(ctx.viewer(), target.menu(), null, target.page());
        });
        bindings.action("command", ctx -> ctx.player().performCommand(ctx.arg()));
        bindings.action("console", consoleAction(allowConsole, log));
        bindings.action("message", ctx -> ctx.player().sendMessage(StyledText.render(ctx.arg())));
        bindings.action("sound", MenuVocabulary::playSound);
    }

    /**
     * Register the generic conditions into {@code bindings}. A condition handler receives the per-open context plus
     * the condition ref's parsed args, the same arg carrier an action reads, so a spec writes {@code perm:some.node}
     * and the node arrives in {@code args.get("value")}. The {@code perm} condition gates an item's view or a click
     * on whether the viewer holds that node through the shared {@link Permissions} port. {@code papi-compare} reads
     * three named args ({@code left}, {@code op}, {@code right}) and compares the two operands after expanding any
     * {@code %token%} in them, so a spec gates on a (PlaceholderAPI-bridged) value such as a balance or vote count.
     * {@code has-prev} / {@code has-next} read the render-time page position so a paginated menu can hide its
     * previous/next arrow on the first/last page rather than showing a dead button. {@code on-page} reads that same
     * position so an item can show only on the pages an operator lists (e.g. {@code on-page:1-3,5}), one-based to line
     * up with {@code %page%}. {@code expr} expands the {@code %token%}s in its expression then runs it through the
     * sandboxed evaluator, so a spec can gate on a computed condition such as {@code expr:"%balance% >= 1000"}.
     */
    public static void registerConditions(MenuBindings bindings, Permissions permissions, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(log, "log");
        bindings.condition("perm", (ctx, args) -> permissions.has(ctx.viewer(), args.getOrDefault("value", "")));
        bindings.condition("has-prev", (ctx, args) -> ctx.page() > 0);
        bindings.condition("has-next", (ctx, args) -> ctx.page() + 1 < ctx.pageCount());
        bindings.condition("on-page", (ctx, args) -> pageInRanges(ctx.page() + 1, args.getOrDefault("value", "")));
        PlaceholderRegistry placeholders = bindings.placeholders();
        bindings.condition("papi-compare", (ctx, args) -> compare(ctx, args, placeholders));
        bindings.condition("expr", exprCondition(placeholders, log));
    }

    /**
     * Build the {@code expr} condition: expand the {@code %token%}s in its raw expression against {@code placeholders}
     * (so a {@code %balance%} compares live), then evaluate the result through the sandboxed {@link Expressions}.
     * A malformed expression fails closed (the condition is {@code false} so the item hides or the click is denied)
     * and the offending text is logged once (de-duplicated so a broken spec does not flood the log on every render).
     */
    private static BiPredicate<MenuContext, Map<String, String>> exprCondition(
            PlaceholderRegistry placeholders, Logger log) {
        Set<String> warned = ConcurrentHashMap.newKeySet();
        return (ctx, args) -> {
            String expression = expand(args.getOrDefault("value", ""), ctx, placeholders);
            try {
                return Expressions.evaluateBoolean(expression);
            } catch (ExpressionException malformed) {
                if (warned.add(expression)) {
                    log.warn("menu expr condition could not be evaluated: {}", expression);
                }
                return false;
            }
        };
    }

    /**
     * Evaluate a {@code papi-compare} ref: expand any {@code %token%} in the {@code left}/{@code right} operands
     * through {@code placeholders} (so a {@code %papi_*%} value compares live), then apply {@code op}. When both
     * operands parse as numbers the comparison is numeric; otherwise only {@code =}/{@code !=} are meaningful and
     * fall back to a string equality test. An unknown operator yields {@code false} rather than throwing, so a spec
     * typo hides the item rather than aborting the menu.
     */
    private static boolean compare(MenuContext ctx, Map<String, String> args, PlaceholderRegistry placeholders) {
        String left = expand(args.getOrDefault("left", ""), ctx, placeholders);
        String op = args.getOrDefault("op", "=").strip();
        String right = expand(args.getOrDefault("right", ""), ctx, placeholders);
        OptionalDouble leftNum = parseNumber(left);
        OptionalDouble rightNum = parseNumber(right);
        if (leftNum.isPresent() && rightNum.isPresent()) {
            return compareNumeric(leftNum.getAsDouble(), op, rightNum.getAsDouble());
        }
        return switch (op) {
            case "=" -> left.equals(right);
            case "!=" -> !left.equals(right);
            default -> false;
        };
    }

    /** Apply a relational/equality {@code op} to two numbers; an unknown operator is {@code false}. */
    private static boolean compareNumeric(double left, String op, double right) {
        return switch (op) {
            case "=" -> left == right;
            case "!=" -> left != right;
            case "<" -> left < right;
            case ">" -> left > right;
            case "<=" -> left <= right;
            case ">=" -> left >= right;
            default -> false;
        };
    }

    /** Replace every {@code %token%} in {@code operand} with its resolved placeholder value (or empty). */
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

    /** The operand as a number, or empty when it is not numeric: the signal to fall back to string comparison. */
    private static OptionalDouble parseNumber(String operand) {
        try {
            return OptionalDouble.of(Double.parseDouble(operand.strip()));
        } catch (NumberFormatException notNumeric) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Whether the one-based {@code page} falls inside {@code spec}. A comma-separated list of {@code N} single pages
     * and {@code A-B} inclusive ranges, with whitespace around any number tolerated (so {@code " 1 - 3 "} and
     * {@code "1-3"} read alike). Pages are one-based to line up with the {@code %page%} placeholder an operator reads,
     * so {@code pages = "2"} shows only on the page {@code %page%} renders as {@code 2}. A blank or malformed spec
     * matches nothing and fails closed: a typo hides the item rather than showing it on every page, the safer default
     * for a visibility gate. Pure and Bukkit-free so a plain-JUnit test can exercise the grammar directly.
     */
    public static boolean pageInRanges(int page, String spec) {
        Objects.requireNonNull(spec, "spec");
        for (String token : COMMAS.split(spec)) {
            String range = token.strip();
            if (range.isEmpty()) {
                continue;
            }
            int dash = range.indexOf('-');
            try {
                if (dash < 0 && Integer.parseInt(range) == page) {
                    return true;
                }
                if (dash > 0
                        && page >= Integer.parseInt(range.substring(0, dash).strip())
                        && page <= Integer.parseInt(range.substring(dash + 1).strip())) {
                    return true;
                }
            } catch (NumberFormatException malformed) {
                // A malformed page token fails closed: skip it so a typo hides the item rather than matching every
                // page.
            }
        }
        return false;
    }

    /**
     * Register the generic placeholders into {@code bindings}. {@code player} expands to the viewer's name, the
     * player the menu is rendered for, whom every player-scoped placeholder resolves against, so on a menu opened for
     * another player it is that target; {@code executor} expands to the name of whoever triggered the open, which is
     * the same player for an ordinary self-open (so {@code %executor%} then reads identically to {@code %player%}) and
     * the opener when the menu was opened for another player; {@code page} to the one-based current page number for
     * display ({@code ctx.page()} is zero-based); and {@code max_page} to the total page count the renderer stamps onto
     * the context before drawing static items, so a "Page %page%/%max_page%" indicator reads the same count its list
     * paginates across.
     */
    public static void registerPlaceholders(MenuBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        bindings.placeholder("player", ctx -> ctx.viewer().name());
        bindings.placeholder("executor", ctx -> ctx.executor().name());
        bindings.placeholder("page", ctx -> String.valueOf(ctx.page() + 1));
        bindings.placeholder("max_page", ctx -> String.valueOf(ctx.pageCount()));
    }

    /**
     * The {@code console} handler: dispatch {@code arg} from the console sender when {@code allowConsole}, else log
     * a warning naming the ignored command and do nothing: privileged dispatch stays opt-in.
     */
    private static Consumer<MenuActionContext> consoleAction(boolean allowConsole, Logger log) {
        if (allowConsole) {
            return ctx -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ctx.arg());
        }
        return ctx ->
                log.warn("menu console action is disabled (custommenus.allow-console=false); ignored: {}", ctx.arg());
    }

    /**
     * A parsed {@code open:<menu> [page]} target: the first whitespace-delimited token is the id of another
     * registered spec to open for the same viewer, and an optional second token is a zero-based start page (a blank,
     * non-numeric, or negative page opens page zero). Parsing is Bukkit-free so a plain-JUnit grammar test can
     * exercise it. The fuller {@code open <menu> <args> <page>} grammar's typed {@code <args>} are Phase-4 typed menu
     * arguments, not parsed here; only the leading menu id and a trailing page are read.
     */
    public record OpenTarget(String menu, int page) {

        public OpenTarget {
            Objects.requireNonNull(menu, "menu");
        }

        public static OpenTarget parse(String value) {
            Objects.requireNonNull(value, "value");
            String[] parts = value.strip().split("\\s+", 2);
            int page = parts.length > 1 ? parsePage(parts[1]) : 0;
            return new OpenTarget(parts[0], page);
        }

        /** The zero-based, non-negative page in {@code token}, or zero when it is blank or not a whole number. */
        private static int parsePage(String token) {
            try {
                return Math.max(0, Integer.parseInt(token.strip()));
            } catch (NumberFormatException notANumber) {
                return 0;
            }
        }
    }

    /**
     * Play a {@code <key> [volume] [pitch]} sound for the clicking player. The optional volume and pitch default to
     * {@code 1f} each and a malformed number falls back to that default, so a bare key still plays at full gain
     * exactly as it always did; a blank key is a no-op rather than an error. The grammar is parsed by the shared
     * {@link SoundActions.SoundArg} so this action and the {@code broadcast-sound} / {@code rawsound} pack read the
     * same argument shape. The key is resolved through the vanilla registry, so an operator may write either the
     * dotted key or the UPPER_SNAKE constant; a name the registry does not know is passed to the client as written,
     * which is how a resource-pack key still reaches it.
     */
    private static void playSound(MenuActionContext ctx) {
        SoundActions.SoundArg sound = SoundActions.SoundArg.parse(ctx.arg());
        if (sound.key().isBlank()) {
            return;
        }
        var at = Objects.requireNonNull(ctx.player().getLocation(), "player location");
        ctx.player().playSound(at, BukkitRegistryKeys.soundKeyName(sound.key()), sound.volume(), sound.pitch());
    }
}
